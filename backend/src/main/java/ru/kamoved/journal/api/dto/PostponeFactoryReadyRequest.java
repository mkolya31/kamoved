package ru.kamoved.journal.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;

public record PostponeFactoryReadyRequest(
    @NotNull LocalDate factoryReadyDate,
    @NotNull @PositiveOrZero Long version
) {
}
