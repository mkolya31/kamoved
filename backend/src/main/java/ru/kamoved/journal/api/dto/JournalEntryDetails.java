package ru.kamoved.journal.api.dto;

import ru.kamoved.journal.domain.EntryType;
import ru.kamoved.journal.domain.ExecutionStatus;
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
    ExecutionStatus executionStatus
) {
}
