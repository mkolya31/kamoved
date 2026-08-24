package ru.kamoved.notification.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.kamoved.notification.domain.EmailNotification;
import ru.kamoved.notification.domain.EmailNotificationStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface EmailNotificationRepository extends JpaRepository<EmailNotification, Long> {

    boolean existsByNotificationKey(String notificationKey);

    Optional<EmailNotification> findByNotificationKey(String notificationKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select notification
        from EmailNotification notification
        where (
            notification.status = :pendingStatus
            and notification.nextAttemptAt <= :now
        ) or (
            notification.status = :processingStatus
            and notification.processingStartedAt <= :staleBefore
        )
        order by notification.nextAttemptAt asc, notification.id asc
        """)
    List<EmailNotification> findDueForUpdate(
        @Param("pendingStatus") EmailNotificationStatus pendingStatus,
        @Param("processingStatus") EmailNotificationStatus processingStatus,
        @Param("now") OffsetDateTime now,
        @Param("staleBefore") OffsetDateTime staleBefore,
        Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select notification from EmailNotification notification where notification.id = :id")
    Optional<EmailNotification> findByIdForUpdate(@Param("id") long id);
}
