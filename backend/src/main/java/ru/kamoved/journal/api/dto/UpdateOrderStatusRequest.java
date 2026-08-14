package ru.kamoved.journal.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import ru.kamoved.journal.domain.ExecutionStatus;

public record UpdateOrderStatusRequest(
    @NotNull ExecutionStatus executionStatus,
    @NotNull @PositiveOrZero Long version
) {
}
