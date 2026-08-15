package ru.kamoved.journal.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import ru.kamoved.journal.domain.ExecutionStatus;
import ru.kamoved.journal.domain.FulfillmentMethod;
import ru.kamoved.journal.domain.PaymentStatus;

import java.math.BigDecimal;
import java.util.List;

public record CreateOrderRequest(
    @NotEmpty
    @Size(max = 100)
    List<@Valid SaleItemRequest> items,

    @Valid
    ContactRequest client,

    @Size(max = 20)
    List<@Valid ContactRequest> additionalContacts,

    PaymentStatus paymentStatus,

    @DecimalMin("0")
    @Digits(integer = 12, fraction = 2)
    BigDecimal prepaymentAmount,

    ExecutionStatus executionStatus,

    FulfillmentMethod fulfillmentMethod,

    @Size(max = 2000)
    String deliveryAddress,

    @Size(max = 5000)
    String comment
) implements OrderDataRequest {
}
