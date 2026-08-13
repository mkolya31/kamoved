package ru.kamoved.journal.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kamoved.auth.domain.AppUser;
import ru.kamoved.auth.persistence.AppUserRepository;
import ru.kamoved.journal.api.dto.ContactRequest;
import ru.kamoved.journal.api.dto.CreateOrderRequest;
import ru.kamoved.journal.api.dto.JournalEntrySummary;
import ru.kamoved.journal.domain.ContactType;
import ru.kamoved.journal.domain.EntryContact;
import ru.kamoved.journal.domain.ExecutionStatus;
import ru.kamoved.journal.domain.FulfillmentMethod;
import ru.kamoved.journal.domain.JournalEntry;
import ru.kamoved.journal.domain.JournalEntryItem;
import ru.kamoved.journal.domain.MoneyCalculator;
import ru.kamoved.journal.domain.PaymentStatus;
import ru.kamoved.journal.domain.PhoneNormalizer;
import ru.kamoved.journal.persistence.JournalEntryRepository;

import java.math.BigDecimal;
import java.util.List;

@Service
public class OrderService {

    private final AppUserRepository users;
    private final JournalEntryRepository entries;
    private final MoneyCalculator moneyCalculator;
    private final PhoneNormalizer phoneNormalizer;
    private final JournalEntryMapper mapper;

    public OrderService(
        AppUserRepository users,
        JournalEntryRepository entries,
        MoneyCalculator moneyCalculator,
        PhoneNormalizer phoneNormalizer,
        JournalEntryMapper mapper
    ) {
        this.users = users;
        this.entries = entries;
        this.moneyCalculator = moneyCalculator;
        this.phoneNormalizer = phoneNormalizer;
        this.mapper = mapper;
    }

    @Transactional
    public JournalEntrySummary create(CreateOrderRequest request, String username) {
        AppUser creator = users.findByUsernameIgnoreCase(username).orElseThrow();
        PaymentStatus paymentStatus = request.paymentStatus() == null
            ? PaymentStatus.UNPAID
            : request.paymentStatus();
        ExecutionStatus executionStatus = request.executionStatus() == null
            ? ExecutionStatus.NEW
            : request.executionStatus();

        String deliveryAddress = validateAndNormalizeAddress(
            request.fulfillmentMethod(), request.deliveryAddress());

        JournalEntry order = JournalEntry.order(
            creator,
            executionStatus,
            paymentStatus,
            null,
            request.fulfillmentMethod(),
            deliveryAddress,
            trimToNull(request.comment())
        );

        request.items().forEach(requestItem -> {
            BigDecimal lineTotal = moneyCalculator.calculateLineTotal(
                requestItem.quantity(), requestItem.unitPrice());
            order.addItem(new JournalEntryItem(
                requestItem.catalogProductId(),
                requestItem.name().trim(),
                requestItem.quantity(),
                requestItem.effectiveUnit(),
                requestItem.unitPrice(),
                lineTotal
            ));
        });

        BigDecimal totalAmount = moneyCalculator.calculateOrderTotal(
            order.getItems().stream().map(JournalEntryItem::getLineTotal).toList());
        order.setTotalAmount(totalAmount);

        BigDecimal prepaymentAmount = validateAndNormalizePrepayment(
            paymentStatus, request.prepaymentAmount(), totalAmount);
        order.setPrepaymentAmount(prepaymentAmount);

        addContact(order, ContactType.CLIENT, request.client());
        List<ContactRequest> additionalContacts = request.additionalContacts() == null
            ? List.of()
            : request.additionalContacts();
        additionalContacts.forEach(contact -> addContact(order, ContactType.ADDITIONAL, contact));

        return mapper.toSummary(entries.saveAndFlush(order));
    }

    private BigDecimal validateAndNormalizePrepayment(
        PaymentStatus paymentStatus,
        BigDecimal prepaymentAmount,
        BigDecimal totalAmount
    ) {
        if (paymentStatus == PaymentStatus.PREPAID) {
            if (prepaymentAmount == null || prepaymentAmount.signum() <= 0) {
                throw new InvalidOrderException("Для предоплаты укажите внесённую сумму");
            }
            if (prepaymentAmount.compareTo(totalAmount) >= 0) {
                throw new InvalidOrderException("Предоплата должна быть меньше суммы заказа");
            }
            return prepaymentAmount;
        }

        if (prepaymentAmount != null && prepaymentAmount.signum() > 0) {
            throw new InvalidOrderException(
                "Сумму предоплаты можно указывать только для статуса «Предоплата»");
        }
        return null;
    }

    private String validateAndNormalizeAddress(
        FulfillmentMethod fulfillmentMethod,
        String deliveryAddress
    ) {
        if (fulfillmentMethod != FulfillmentMethod.DELIVERY) {
            return null;
        }

        String normalizedAddress = trimToNull(deliveryAddress);
        if (normalizedAddress == null) {
            throw new InvalidOrderException("Для доставки укажите адрес");
        }
        return normalizedAddress;
    }

    private void addContact(JournalEntry order, ContactType type, ContactRequest request) {
        if (request == null) {
            return;
        }

        String name = trimToNull(request.name());
        String phone = trimToNull(request.phone());
        String comment = trimToNull(request.comment());
        if (name == null && phone == null && comment == null) {
            return;
        }

        String normalizedPhone = phoneNormalizer.normalize(phone);
        if (phone != null && normalizedPhone == null) {
            throw new InvalidOrderException("Телефон должен содержать цифры");
        }

        order.addContact(new EntryContact(type, name, phone, normalizedPhone, comment));
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
