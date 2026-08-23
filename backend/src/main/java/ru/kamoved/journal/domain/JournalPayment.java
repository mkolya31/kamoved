package ru.kamoved.journal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import ru.kamoved.auth.domain.AppUser;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "journal_payment")
public class JournalPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "journal_entry_id", nullable = false)
    private JournalEntry journalEntry;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 30)
    private PaymentMethod paymentMethod;

    private String comment;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private AppUser createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "voided_at")
    private OffsetDateTime voidedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voided_by")
    private AppUser voidedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "correction_of_id", unique = true)
    private JournalPayment correctionOf;

    @Column(name = "correction_reason")
    private String correctionReason;

    protected JournalPayment() {
    }

    private JournalPayment(
        BigDecimal amount,
        PaymentMethod paymentMethod,
        String comment,
        OffsetDateTime receivedAt,
        AppUser createdBy,
        JournalPayment correctionOf,
        String correctionReason
    ) {
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.comment = comment;
        this.receivedAt = receivedAt;
        this.createdBy = createdBy;
        this.createdAt = OffsetDateTime.now();
        this.correctionOf = correctionOf;
        this.correctionReason = correctionReason;
    }

    public static JournalPayment received(
        BigDecimal amount,
        PaymentMethod paymentMethod,
        String comment,
        AppUser createdBy
    ) {
        return new JournalPayment(
            amount,
            paymentMethod,
            comment,
            OffsetDateTime.now(),
            createdBy,
            null,
            null
        );
    }

    public JournalPayment corrected(
        BigDecimal correctedAmount,
        PaymentMethod correctedMethod,
        String correctedComment,
        String reason,
        AppUser correctedBy
    ) {
        voidedAt = OffsetDateTime.now();
        voidedBy = correctedBy;
        return new JournalPayment(
            correctedAmount,
            correctedMethod,
            correctedComment,
            receivedAt,
            correctedBy,
            this,
            reason
        );
    }

    void attachTo(JournalEntry entry) {
        journalEntry = entry;
    }

    public Long getId() {
        return id;
    }

    public JournalEntry getJournalEntry() {
        return journalEntry;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public String getComment() {
        return comment;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }

    public AppUser getCreatedBy() {
        return createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getVoidedAt() {
        return voidedAt;
    }

    public AppUser getVoidedBy() {
        return voidedBy;
    }

    public JournalPayment getCorrectionOf() {
        return correctionOf;
    }

    public String getCorrectionReason() {
        return correctionReason;
    }

    public boolean isActive() {
        return voidedAt == null;
    }
}
