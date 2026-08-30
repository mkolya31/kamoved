package ru.kamoved.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import ru.kamoved.auth.domain.AppUser;

import java.time.OffsetDateTime;

@Entity
@Table(name = "email_notification")
public class EmailNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_key", nullable = false, unique = true, length = 255)
    private String notificationKey;

    @ManyToOne(optional = false)
    @JoinColumn(name = "recipient_user_id", nullable = false)
    private AppUser recipient;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(name = "text_body", nullable = false, columnDefinition = "text")
    private String textBody;

    @Column(name = "html_body", columnDefinition = "text")
    private String htmlBody;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmailNotificationStatus status;

    @Column(name = "scheduled_at", nullable = false)
    private OffsetDateTime scheduledAt;

    @Column(name = "next_attempt_at", nullable = false)
    private OffsetDateTime nextAttemptAt;

    @Column(name = "processing_started_at")
    private OffsetDateTime processingStartedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected EmailNotification() {
    }

    private EmailNotification(
        String notificationKey,
        AppUser recipient,
        String subject,
        String textBody,
        String htmlBody,
        OffsetDateTime scheduledAt,
        OffsetDateTime now
    ) {
        this.notificationKey = notificationKey;
        this.recipient = recipient;
        this.subject = subject;
        this.textBody = textBody;
        this.htmlBody = htmlBody;
        this.status = EmailNotificationStatus.PENDING;
        this.scheduledAt = scheduledAt;
        this.nextAttemptAt = scheduledAt;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static EmailNotification pending(
        String notificationKey,
        AppUser recipient,
        String subject,
        String textBody,
        String htmlBody,
        OffsetDateTime scheduledAt,
        OffsetDateTime now
    ) {
        return new EmailNotification(
            notificationKey,
            recipient,
            subject,
            textBody,
            htmlBody,
            scheduledAt,
            now
        );
    }

    public void startProcessing(OffsetDateTime now) {
        status = EmailNotificationStatus.PROCESSING;
        processingStartedAt = now;
        attemptCount++;
        lastError = null;
        updatedAt = now;
    }

    public void markSent(OffsetDateTime now) {
        requireProcessing();
        status = EmailNotificationStatus.SENT;
        sentAt = now;
        processingStartedAt = null;
        lastError = null;
        updatedAt = now;
    }

    public void scheduleRetry(
        OffsetDateTime nextAttemptAt,
        String error,
        OffsetDateTime now
    ) {
        requireProcessing();
        status = EmailNotificationStatus.PENDING;
        this.nextAttemptAt = nextAttemptAt;
        processingStartedAt = null;
        lastError = error;
        updatedAt = now;
    }

    public void cancel(OffsetDateTime now) {
        requireProcessing();
        status = EmailNotificationStatus.CANCELLED;
        processingStartedAt = null;
        lastError = null;
        updatedAt = now;
    }

    private void requireProcessing() {
        if (status != EmailNotificationStatus.PROCESSING) {
            throw new IllegalStateException("Уведомление не находится в процессе отправки");
        }
    }

    public Long getId() {
        return id;
    }

    public String getNotificationKey() {
        return notificationKey;
    }

    public AppUser getRecipient() {
        return recipient;
    }

    public String getSubject() {
        return subject;
    }

    public String getTextBody() {
        return textBody;
    }

    public String getHtmlBody() {
        return htmlBody;
    }

    public EmailNotificationStatus getStatus() {
        return status;
    }

    public OffsetDateTime getScheduledAt() {
        return scheduledAt;
    }

    public OffsetDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }

    public OffsetDateTime getProcessingStartedAt() {
        return processingStartedAt;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public OffsetDateTime getSentAt() {
        return sentAt;
    }

    public String getLastError() {
        return lastError;
    }
}
