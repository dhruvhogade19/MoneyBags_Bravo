package com.moneybags.notification.notification.service;

import com.moneybags.notification.common.exception.InvalidCustomerEmailException;
import com.moneybags.notification.common.exception.IdempotencyConflictException;
import com.moneybags.notification.common.exception.NotificationTemplateNotFoundException;
import com.moneybags.notification.notification.domain.DeliveryAttemptResult;
import com.moneybags.notification.notification.dto.CreateNotificationRequest;
import com.moneybags.notification.notification.dto.KycStatus;
import com.moneybags.notification.notification.dto.KycStatusNotificationRequest;
import com.moneybags.notification.notification.dto.NotificationResponse;
import com.moneybags.notification.notification.entity.DeliveryAttempt;
import com.moneybags.notification.notification.entity.Notification;
import com.moneybags.notification.notification.integration.CifClient;
import com.moneybags.notification.notification.integration.CustomerProfile;
import com.moneybags.notification.notification.integration.EmailSender;
import com.moneybags.notification.notification.integration.MailSenderProperties;
import com.moneybags.notification.notification.repository.DeliveryAttemptRepository;
import com.moneybags.notification.notification.repository.NotificationRepository;
import com.moneybags.notification.notification.repository.NotificationTemplateRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationService.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Set<String> RESERVED_CUSTOMER_VARIABLES = Set.of("firstName", "lastName");

    private final NotificationRepository notificationRepository;
    private final NotificationTemplateRepository templateRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final CifClient cifClient;
    private final EmailSender emailSender;
    private final MailSenderProperties mailProperties;
    private final TemplateRenderer templateRenderer;
    private final NotificationMapper notificationMapper;
    private final RequestFingerprintService fingerprintService;

    public NotificationService(
            NotificationRepository notificationRepository,
            NotificationTemplateRepository templateRepository,
            DeliveryAttemptRepository deliveryAttemptRepository,
            CifClient cifClient,
            EmailSender emailSender,
            MailSenderProperties mailProperties,
            TemplateRenderer templateRenderer,
            NotificationMapper notificationMapper,
            RequestFingerprintService fingerprintService) {
        this.notificationRepository = notificationRepository;
        this.templateRepository = templateRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.cifClient = cifClient;
        this.emailSender = emailSender;
        this.mailProperties = mailProperties;
        this.templateRenderer = templateRenderer;
        this.notificationMapper = notificationMapper;
        this.fingerprintService = fingerprintService;
    }

    @Transactional
    public NotificationCreationResult createOrReplay(CreateNotificationRequest request, String idempotencyKey) {
        String fingerprint = fingerprintService.fingerprint(request);
        var existing = notificationRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            if (existing.get().getRequestFingerprint().equals(fingerprint)) {
                return new NotificationCreationResult(notificationMapper.toResponse(existing.get()), true);
            }
            throw new IdempotencyConflictException(idempotencyKey);
        }
        return new NotificationCreationResult(create(request, idempotencyKey, fingerprint), false);
    }

    @Transactional
    public NotificationCreationResult createKycStatusNotification(KycStatusNotificationRequest request) {
        var notificationType = switch (request.kycStatus()) {
            case APPROVED -> com.moneybags.notification.notification.domain.NotificationType.KYC_APPROVED;
            case REJECTED -> com.moneybags.notification.notification.domain.NotificationType.KYC_REJECTED;
        };
        Map<String, String> variables = request.kycStatus() == KycStatus.REJECTED
                ? Map.of("rejectionReason", requiredRejectionReason(request))
                : Map.of();
        String sourceReference = "KYC-" + request.cifId() + "-" + request.kycStatus();
        CreateNotificationRequest notificationRequest = new CreateNotificationRequest(
                request.cifId(), notificationType, sourceReference, variables);
        return createOrReplay(notificationRequest, "kyc-" + request.cifId() + "-" + request.kycStatus());
    }

    private NotificationResponse create(CreateNotificationRequest request, String idempotencyKey, String fingerprint) {
        rejectReservedVariables(request.templateVariables());
        CustomerProfile customer = cifClient.getCustomer(request.cifId());
        validateCustomer(customer, request.cifId());
        var template = templateRepository.findByNotificationTypeAndActiveTrue(request.notificationType())
                .orElseThrow(() -> new NotificationTemplateNotFoundException(request.notificationType()));
        templateRenderer.validateSourceVariables(template, request.templateVariables());
        RenderedEmail renderedEmail = templateRenderer.render(template, variablesFor(customer, request.templateVariables()));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Notification notification = notificationRepository.saveAndFlush(new Notification(
                request.cifId(), request.notificationType(), request.sourceReference(), idempotencyKey, fingerprint,
                customer.email(), renderedEmail.subject(), renderedEmail.body(), now));

        try {
            emailSender.send(customer.email(), renderedEmail.subject(), renderedEmail.body());
            OffsetDateTime sentAt = OffsetDateTime.now(ZoneOffset.UTC);
            notification.markSent(sentAt);
            deliveryAttemptRepository.save(new DeliveryAttempt(
                    notification, mailProperties.provider(), DeliveryAttemptResult.SENT, null, null, sentAt));
        } catch (MailException exception) {
            LOGGER.warn("SMTP delivery failed for notificationId={}, exceptionType={}, rootCauseType={}",
                    notification.getId(), exception.getClass().getSimpleName(),
                    exception.getMostSpecificCause().getClass().getSimpleName());
            notification.markFailed();
            deliveryAttemptRepository.save(new DeliveryAttempt(
                    notification, mailProperties.provider(), DeliveryAttemptResult.FAILED, null,
                    safeErrorMessage(exception), OffsetDateTime.now(ZoneOffset.UTC)));
        }
        return notificationMapper.toResponse(notification);
    }

    private void validateCustomer(CustomerProfile customer, Long requestedCifId) {
        if (customer == null || !requestedCifId.equals(customer.cifId())
                || customer.email() == null || !EMAIL_PATTERN.matcher(customer.email()).matches()) {
            throw new InvalidCustomerEmailException(requestedCifId);
        }
    }

    private Map<String, String> variablesFor(CustomerProfile customer, Map<String, String> requestVariables) {
        Map<String, String> variables = new HashMap<>(requestVariables);
        variables.put("firstName", customer.firstName() == null || customer.firstName().isBlank()
                ? "Customer" : customer.firstName());
        variables.put("lastName", customer.lastName() == null ? "" : customer.lastName());
        return variables;
    }

    private void rejectReservedVariables(Map<String, String> requestVariables) {
        for (String variable : requestVariables.keySet()) {
            if (RESERVED_CUSTOMER_VARIABLES.contains(variable)) {
                throw new IllegalArgumentException("Template variable is managed by CIF: " + variable);
            }
        }
    }

    private String requiredRejectionReason(KycStatusNotificationRequest request) {
        if (request.rejectionReason() == null || request.rejectionReason().isBlank()) {
            throw new IllegalArgumentException("rejectionReason is required when kycStatus is REJECTED");
        }
        return request.rejectionReason();
    }

    private String safeErrorMessage(MailException exception) {
        String message = exception.getMessage();
        return message == null ? "Mail delivery failed" : message.substring(0, Math.min(message.length(), 1_000));
    }
}
