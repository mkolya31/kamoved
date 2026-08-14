package ru.kamoved.journal.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import ru.kamoved.journal.domain.PaymentStatus;

import java.math.BigDecimal;

public record UpdateOrderPaymentRequest(
    @NotNull PaymentStatus paymentStatus,
    BigDecimal paidAmount,
    @NotNull @PositiveOrZero Long version
) {
}
