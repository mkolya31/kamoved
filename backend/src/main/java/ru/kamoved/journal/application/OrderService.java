package ru.kamoved.journal.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kamoved.auth.domain.AppUser;
import ru.kamoved.auth.persistence.AppUserRepository;
import ru.kamoved.journal.api.dto.ContactRequest;
import ru.kamoved.journal.api.dto.CreateOrderRequest;
import ru.kamoved.journal.api.dto.JournalEntryDetails;
import ru.kamoved.journal.api.dto.JournalEntrySummary;
import ru.kamoved.journal.api.dto.OrderDataRequest;
import ru.kamoved.journal.api.dto.UpdateOrderRequest;
import ru.kamoved.journal.domain.ContactType;
import ru.kamoved.journal.domain.EntryContact;
import ru.kamoved.journal.domain.EntryType;
import ru.kamoved.journal.domain.ExecutionStatus;
import ru.kamoved.journal.domain.FulfillmentMethod;
import ru.kamoved.journal.domain.JournalEntry;
import ru.kamoved.journal.domain.JournalEntryItem;
import ru.kamoved.journal.domain.MoneyCalculator;
import ru.kamoved.journal.domain.PaymentStatus;
import ru.kamoved.journal.domain.PhoneNormalizer;
import ru.kamoved.journal.persistence.JournalEntryRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
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
        JournalEntry order = JournalEntry.order(
            creator,
            ExecutionStatus.NEW,
            PaymentStatus.UNPAID,
            null,
            null,
            null,
            null
        );

        applyOrderData(order, request);
        return mapper.toSummary(entries.saveAndFlush(order));
    }

    @Transactional
    public JournalEntryDetails update(long orderId, UpdateOrderRequest request) {
        JournalEntry order = findOrderWithExpectedVersion(orderId, request.version());
        applyOrderData(order, request);
        return mapper.toDetails(entries.saveAndFlush(order));
    }

    private void applyOrderData(JournalEntry order, OrderDataRequest request) {
        PaymentStatus paymentStatus = request.paymentStatus() == null
            ? PaymentStatus.UNPAID
            : request.paymentStatus();
        ExecutionStatus executionStatus = request.executionStatus() == null
            ? ExecutionStatus.NEW
            : request.executionStatus();
        String deliveryAddress = validateAndNormalizeAddress(
            request.fulfillmentMethod(), request.deliveryAddress());

        List<JournalEntryItem> orderItems = request.items().stream().map(requestItem -> {
            BigDecimal lineTotal = moneyCalculator.calculateLineTotal(
                requestItem.quantity(), requestItem.unitPrice());
            return new JournalEntryItem(
                requestItem.catalogProductId(),
                requestItem.name().trim(),
                requestItem.quantity(),
                requestItem.effectiveUnit(),
                requestItem.unitPrice(),
                lineTotal
            );
        }).toList();

        BigDecimal totalAmount = moneyCalculator.calculateOrderTotal(
            orderItems.stream().map(JournalEntryItem::getLineTotal).toList());
        BigDecimal prepaymentAmount = validateAndNormalizePrepayment(
            paymentStatus, request.prepaymentAmount(), totalAmount);

        order.replaceItems(orderItems);
        order.setTotalAmount(totalAmount);
        order.changePayment(paymentStatus, prepaymentAmount);
        order.changeExecutionStatus(executionStatus);
        order.changeFulfillment(request.fulfillmentMethod(), deliveryAddress);
        order.changeComment(trimToNull(request.comment()));

        List<EntryContact> contacts = new ArrayList<>();
        EntryContact client = createContact(ContactType.CLIENT, request.client());
        if (client != null) {
            contacts.add(client);
        }
        List<ContactRequest> additionalContacts = request.additionalContacts() == null
            ? List.of()
            : request.additionalContacts();
        additionalContacts.stream()
            .map(contact -> createContact(ContactType.ADDITIONAL, contact))
            .filter(contact -> contact != null)
            .forEach(contacts::add);
        order.replaceContacts(contacts);
    }

    @Transactional
    public JournalEntrySummary updateExecutionStatus(
        long orderId,
        ExecutionStatus executionStatus,
        long expectedVersion
    ) {
        JournalEntry order = findOrderWithExpectedVersion(orderId, expectedVersion);

        order.changeExecutionStatus(executionStatus);
        return mapper.toSummary(entries.saveAndFlush(order));
    }

    @Transactional
    public JournalEntrySummary updatePayment(
        long orderId,
        PaymentStatus paymentStatus,
        BigDecimal paidAmount,
        long expectedVersion
    ) {
        JournalEntry order = findOrderWithExpectedVersion(orderId, expectedVersion);
        BigDecimal prepaymentAmount = validateAndNormalizePrepayment(
            paymentStatus, paidAmount, order.getTotalAmount());

        order.changePayment(paymentStatus, prepaymentAmount);
        return mapper.toSummary(entries.saveAndFlush(order));
    }

    private JournalEntry findOrderWithExpectedVersion(long orderId, long expectedVersion) {
        JournalEntry order = entries.findById(orderId).orElseThrow(OrderNotFoundException::new);
        if (order.getType() != EntryType.ORDER) {
            throw new OrderNotFoundException();
        }
        if (order.getVersion() != expectedVersion) {
            throw new OrderVersionConflictException();
        }
        return order;
    }

    private BigDecimal validateAndNormalizePrepayment(
        PaymentStatus paymentStatus,
        BigDecimal paidAmount,
        BigDecimal totalAmount
    ) {
        if (paidAmount != null && paidAmount.signum() < 0) {
            throw new InvalidOrderException("Внесённая сумма не может быть отрицательной");
        }

        if (paymentStatus == PaymentStatus.PREPAID) {
            if (paidAmount == null || paidAmount.signum() <= 0) {
                throw new InvalidOrderException("Для предоплаты укажите внесённую сумму");
            }
            if (paidAmount.compareTo(totalAmount) >= 0) {
                throw new InvalidOrderException("Предоплата должна быть меньше суммы заказа");
            }
            return paidAmount;
        }

        if (paidAmount != null && paidAmount.signum() > 0) {
            throw new InvalidOrderException(
                "Внесённую сумму можно указывать только для статуса «Предоплата»");
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

    private EntryContact createContact(ContactType type, ContactRequest request) {
        if (request == null) {
            return null;
        }

        String name = trimToNull(request.name());
        String phone = trimToNull(request.phone());
        String comment = trimToNull(request.comment());
        if (name == null && phone == null && comment == null) {
            return null;
        }

        String normalizedPhone = phoneNormalizer.normalize(phone);
        if (phone != null && normalizedPhone == null) {
            throw new InvalidOrderException("Телефон должен содержать цифры");
        }

        return new EntryContact(type, name, phone, normalizedPhone, comment);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
