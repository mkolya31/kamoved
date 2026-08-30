package ru.kamoved.journal.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import ru.kamoved.journal.domain.ExecutionStatus;
import ru.kamoved.journal.domain.FulfillmentMethod;
import java.util.List;
import java.time.LocalDate;

public record CreateOrderRequest(
    @NotEmpty
    @Size(max = 100)
    List<@Valid SaleItemRequest> items,

    @Valid
    ContactRequest client,

    @Size(max = 20)
    List<@Valid ContactRequest> additionalContacts,

    @Valid
    CreatePaymentRequest initialPayment,

    ExecutionStatus executionStatus,

    FulfillmentMethod fulfillmentMethod,

    @Size(max = 2000)
    String deliveryAddress,

    @Size(max = 5000)
    String comment,

    LocalDate factoryReadyDate
) implements OrderDataRequest {
}
