package ru.kamoved.journal.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateSaleRequest(
    @NotEmpty
    @Size(max = 100)
    List<@Valid SaleItemRequest> items
) {
}

