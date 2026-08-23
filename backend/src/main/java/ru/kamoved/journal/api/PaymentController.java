package ru.kamoved.journal.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.kamoved.journal.api.dto.CorrectPaymentRequest;
import ru.kamoved.journal.api.dto.CreatePaymentRequest;
import ru.kamoved.journal.api.dto.JournalEntryDetails;
import ru.kamoved.journal.api.dto.JournalEntrySummary;
import ru.kamoved.journal.application.PaymentService;

@RestController
@RequestMapping("/api")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/orders/{orderId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    JournalEntrySummary addOrderPayment(
        @PathVariable long orderId,
        @Valid @RequestBody CreatePaymentRequest request,
        Authentication authentication
    ) {
        return paymentService.addOrderPayment(orderId, request, authentication.getName());
    }

    @PostMapping("/payments/{paymentId}/corrections")
    JournalEntryDetails correctPayment(
        @PathVariable long paymentId,
        @Valid @RequestBody CorrectPaymentRequest request,
        Authentication authentication
    ) {
        return paymentService.correct(paymentId, request, authentication.getName());
    }
}
