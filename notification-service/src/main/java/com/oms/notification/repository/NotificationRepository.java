package com.oms.notification.repository;

import com.oms.notification.model.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, String> {
    List<Notification> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    long countByUserIdAndIsReadFalse(String userId);
    List<Notification> findByUserIdAndIsReadFalse(String userId);

    /** Idempotency check for Kafka consumers — has this event already been processed? */
    boolean existsByEventId(String eventId);
}
