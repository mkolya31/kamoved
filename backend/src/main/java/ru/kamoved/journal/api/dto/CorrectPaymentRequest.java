package ru.kamoved.journal.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.kamoved.journal.domain.PaymentMethod;

import java.math.BigDecimal;

public record CorrectPaymentRequest(
    @DecimalMin("0.01")
    @Digits(integer = 12, fraction = 2)
    BigDecimal amount,
    @NotNull PaymentMethod paymentMethod,
    @Size(max = 5000) String comment,
    @NotBlank @Size(max = 2000) String reason
) {
}
