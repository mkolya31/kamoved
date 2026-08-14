package ru.kamoved.journal.api.dto;

import ru.kamoved.journal.domain.EntryType;
import ru.kamoved.journal.domain.ExecutionStatus;
import ru.kamoved.journal.domain.FulfillmentMethod;
import ru.kamoved.journal.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
public record JournalEntrySummary(
    Long id,
    EntryType type,
    OffsetDateTime createdAt,
    JournalItemSummary mainItem,
    int itemsCount,
    BigDecimal totalAmount,
    PaymentStatus paymentStatus,
    BigDecimal prepaymentAmount,
    BigDecimal remainingAmount,
    ExecutionStatus executionStatus,
    String clientName,
    String clientPhone,
    FulfillmentMethod fulfillmentMethod,
    String deliveryAddress,
    long version
) {
}
