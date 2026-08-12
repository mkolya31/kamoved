package ru.kamoved.journal.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.kamoved.journal.domain.UnitOfMeasure;

import java.math.BigDecimal;

public record SaleItemRequest(
    Long catalogProductId,

    @NotBlank
    @Size(max = 500)
    String name,

    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    @Digits(integer = 11, fraction = 3)
    BigDecimal quantity,

    UnitOfMeasure unit,

    @NotNull
    @DecimalMin("0")
    @Digits(integer = 12, fraction = 2)
    BigDecimal unitPrice
) {
    public UnitOfMeasure effectiveUnit() {
        return unit == null ? UnitOfMeasure.PIECE : unit;
    }
}
