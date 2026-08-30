package ru.kamoved.journal.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record FactoryReadyActionRequest(
    @NotNull @PositiveOrZero Long version
) {
}
