package com.moneybags.notification.common.config;

import com.moneybags.notification.notification.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component("notificationAuthorization")
public class NotificationAuthorization {
    private final NotificationRepository notifications;
    private final boolean securityEnabled;

    public NotificationAuthorization(NotificationRepository notifications,
                                     @Value("${moneybags.security.enabled:true}") boolean securityEnabled) {
        this.notifications = notifications;
        this.securityEnabled = securityEnabled;
    }

    public boolean canUseCif(Authentication authentication, Long cifId) {
        return !securityEnabled || privileged(authentication) || owns(authentication, cifId);
    }

    public boolean canAccess(Authentication authentication, Long notificationId) {
        if (!securityEnabled || privileged(authentication)) return true;
        return notifications.findById(notificationId)
                .map(notification -> owns(authentication, notification.getCifId())).orElse(false);
    }

    private boolean privileged(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_BANK_ADMIN") || a.getAuthority().equals("SCOPE_notification:admin"));
    }

    private boolean owns(Authentication authentication, Long cifId) {
        if (!(authentication instanceof JwtAuthenticationToken jwt)) return false;
        String customerId = jwt.getToken().getClaimAsString("customer_id");
        return customerId != null && customerId.equals(String.valueOf(cifId));
    }
}
