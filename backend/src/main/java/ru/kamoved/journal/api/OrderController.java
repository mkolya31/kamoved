package ru.kamoved.journal.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.kamoved.journal.api.dto.CreateOrderRequest;
import ru.kamoved.journal.api.dto.JournalEntrySummary;
import ru.kamoved.journal.application.OrderService;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    JournalEntrySummary create(
        @Valid @RequestBody CreateOrderRequest request,
        Authentication authentication
    ) {
        return orderService.create(request, authentication.getName());
    }
}
