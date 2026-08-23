package ru.kamoved.journal.api.dto;

import ru.kamoved.journal.domain.ExecutionStatus;
import ru.kamoved.journal.domain.FulfillmentMethod;
import java.util.List;

public interface OrderDataRequest {

    List<SaleItemRequest> items();

    ContactRequest client();

    List<ContactRequest> additionalContacts();

    ExecutionStatus executionStatus();

    FulfillmentMethod fulfillmentMethod();

    String deliveryAddress();

    String comment();
}
