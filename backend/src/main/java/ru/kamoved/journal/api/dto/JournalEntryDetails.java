package ru.kamoved.journal.api.dto;

import ru.kamoved.journal.domain.EntryType;
import ru.kamoved.journal.domain.ExecutionStatus;
import ru.kamoved.journal.domain.FulfillmentMethod;
import ru.kamoved.journal.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record JournalEntryDetails(
    Long id,
    EntryType type,
    OffsetDateTime createdAt,
    List<JournalItemSummary> items,
    BigDecimal totalAmount,
    PaymentStatus paymentStatus,
    BigDecimal prepaymentAmount,
    BigDecimal paidAmount,
    BigDecimal remainingAmount,
    List<PaymentDetails> payments,
    ExecutionStatus executionStatus,
    JournalContactDetails client,
    List<JournalContactDetails> additionalContacts,
    FulfillmentMethod fulfillmentMethod,
    String deliveryAddress,
    String comment,
    String createdByDisplayName,
    OffsetDateTime updatedAt,
    long version
) {
}
