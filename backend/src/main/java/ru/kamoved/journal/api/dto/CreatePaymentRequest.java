package ru.kamoved.journal.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.kamoved.journal.domain.PaymentMethod;

import java.math.BigDecimal;

public record CreatePaymentRequest(
    @NotNull
    @DecimalMin("0.01")
    @Digits(integer = 12, fraction = 2)
    BigDecimal amount,
    @NotNull PaymentMethod paymentMethod,
    @Size(max = 5000) String comment
) {
}
