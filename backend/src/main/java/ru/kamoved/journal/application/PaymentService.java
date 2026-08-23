package ru.kamoved.journal.application;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.kamoved.auth.domain.AppUser;
import ru.kamoved.auth.persistence.AppUserRepository;
import ru.kamoved.journal.api.dto.CorrectPaymentRequest;
import ru.kamoved.journal.api.dto.CreatePaymentRequest;
import ru.kamoved.journal.api.dto.JournalEntryDetails;
import ru.kamoved.journal.api.dto.JournalEntrySummary;
import ru.kamoved.journal.domain.EntryType;
import ru.kamoved.journal.domain.JournalEntry;
import ru.kamoved.journal.domain.JournalPayment;
import ru.kamoved.journal.persistence.JournalEntryRepository;
import ru.kamoved.journal.persistence.JournalPaymentRepository;

import java.math.BigDecimal;

@Service
public class PaymentService {

    private final AppUserRepository users;
    private final JournalEntryRepository entries;
    private final JournalPaymentRepository payments;
    private final JournalEntryMapper mapper;

    public PaymentService(
        AppUserRepository users,
        JournalEntryRepository entries,
        JournalPaymentRepository payments,
        JournalEntryMapper mapper
    ) {
        this.users = users;
        this.entries = entries;
        this.payments = payments;
        this.mapper = mapper;
    }

    @Transactional
    public JournalEntrySummary addOrderPayment(
        long orderId,
        CreatePaymentRequest request,
        String username
    ) {
        JournalEntry order = entries.findByIdForUpdate(orderId)
            .orElseThrow(OrderNotFoundException::new);
        if (order.getType() != EntryType.ORDER) {
            throw new OrderNotFoundException();
        }

        BigDecimal remainingAmount = order.getTotalAmount().subtract(order.getPaidAmount());
        validateAmount(request.amount(), remainingAmount);
        order.addPayment(JournalPayment.received(
            request.amount(),
            request.paymentMethod(),
            trimToNull(request.comment()),
            findUser(username)
        ));
        return mapper.toSummary(entries.saveAndFlush(order));
    }

    @Transactional
    public JournalEntryDetails correct(
        long paymentId,
        CorrectPaymentRequest request,
        String username
    ) {
        JournalPayment snapshot = payments.findById(paymentId).orElseThrow(this::paymentNotFound);
        JournalEntry entry = entries.findByIdForUpdate(snapshot.getJournalEntry().getId())
            .orElseThrow(this::paymentNotFound);
        JournalPayment payment = entry.getPayments().stream()
            .filter(candidate -> candidate.getId().equals(paymentId))
            .findFirst()
            .orElseThrow(this::paymentNotFound);
        if (!payment.isActive()) {
            throw new InvalidPaymentException("Этот платёж уже исправлен");
        }

        BigDecimal correctedAmount = request.amount() == null
            ? payment.getAmount()
            : request.amount();
        if (entry.getType() == EntryType.SALE
            && correctedAmount.compareTo(payment.getAmount()) != 0) {
            throw new InvalidPaymentException("Сумму платежа продажи изменить нельзя");
        }

        BigDecimal paidWithoutCurrent = entry.getPaidAmount().subtract(payment.getAmount());
        validateAmount(correctedAmount, entry.getTotalAmount().subtract(paidWithoutCurrent));

        AppUser corrector = findUser(username);
        JournalPayment corrected = payment.corrected(
            correctedAmount,
            request.paymentMethod(),
            trimToNull(request.comment()),
            request.reason().trim(),
            corrector
        );
        entry.addPayment(corrected);
        return mapper.toDetails(entries.saveAndFlush(entry));
    }

    private void validateAmount(BigDecimal amount, BigDecimal remainingAmount) {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidPaymentException("Сумма платежа должна быть больше нуля");
        }
        if (amount.compareTo(remainingAmount) > 0) {
            throw new InvalidPaymentException("Сумма платежа не может превышать остаток заказа");
        }
    }

    private AppUser findUser(String username) {
        return users.findByUsernameIgnoreCase(username).orElseThrow();
    }

    private ResponseStatusException paymentNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Платёж не найден");
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
