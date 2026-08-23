package ru.kamoved.journal.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.kamoved.journal.domain.PaymentMethod;

import java.util.List;

public record CreateSaleRequest(
    @NotEmpty
    @Size(max = 100)
    List<@Valid SaleItemRequest> items,

    @NotNull
    PaymentMethod paymentMethod,

    @Size(max = 5000)
    String paymentComment,

    @Size(max = 5000)
    String comment
) {
}
