package com.moneybags.notification.notification.service;

import com.moneybags.notification.common.exception.NotificationNotFoundException;
import com.moneybags.notification.notification.dto.NotificationResponse;
import com.moneybags.notification.notification.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NotificationHistoryService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;

    public NotificationHistoryService(NotificationRepository notificationRepository, NotificationMapper notificationMapper) {
        this.notificationRepository = notificationRepository;
        this.notificationMapper = notificationMapper;
    }

    public Page<NotificationResponse> getHistory(Long cifId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return notificationRepository.findByCifId(cifId, pageRequest).map(notificationMapper::toResponse);
    }

    public NotificationResponse getById(Long notificationId) {
        return notificationRepository.findById(notificationId)
                .map(notificationMapper::toResponse)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
    }
}
