package ru.kamoved.journal.api.dto;

import ru.kamoved.journal.domain.PaymentMethod;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PaymentDetails(
    Long id,
    BigDecimal amount,
    PaymentMethod paymentMethod,
    String comment,
    OffsetDateTime receivedAt,
    String createdByDisplayName,
    OffsetDateTime createdAt,
    boolean active,
    OffsetDateTime voidedAt,
    String voidedByDisplayName,
    Long correctionOfId,
    String correctionReason
) {
}
