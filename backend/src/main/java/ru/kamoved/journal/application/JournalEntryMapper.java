package ru.kamoved.journal.application;

import org.springframework.stereotype.Component;
import ru.kamoved.journal.api.dto.JournalEntryDetails;
import ru.kamoved.journal.api.dto.JournalEntrySummary;
import ru.kamoved.journal.api.dto.JournalContactDetails;
import ru.kamoved.journal.api.dto.JournalItemSummary;
import ru.kamoved.journal.api.dto.JournalSearchMatch;
import ru.kamoved.journal.api.dto.PaymentDetails;
import ru.kamoved.journal.domain.ContactType;
import ru.kamoved.journal.domain.EntryContact;
import ru.kamoved.journal.domain.JournalEntry;
import ru.kamoved.journal.domain.JournalEntryItem;
import ru.kamoved.journal.domain.JournalPayment;
import ru.kamoved.journal.domain.PaymentStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class JournalEntryMapper {

    public JournalEntrySummary toSummary(JournalEntry entry) {
        JournalItemSummary mainItem = entry.getItems().stream()
            .findFirst()
            .map(this::toItem)
            .orElse(null);
        EntryContact client = entry.getContacts().stream()
            .filter(contact -> contact.getType() == ContactType.CLIENT)
            .findFirst()
            .orElse(null);

        return new JournalEntrySummary(
            entry.getId(),
            entry.getType(),
            entry.getCreatedAt(),
            mainItem,
            entry.getItems().size(),
            entry.getTotalAmount(),
            entry.getPaymentStatus(),
            legacyPrepaymentAmount(entry),
            entry.getPaidAmount(),
            remainingAmount(entry),
            entry.getExecutionStatus(),
            client == null ? null : client.getName(),
            client == null ? null : client.getPhone(),
            entry.getFulfillmentMethod(),
            entry.getDeliveryAddress(),
            entry.getFactoryReadyDate(),
            entry.isFactoryReadyAttention(),
            entry.getVersion(),
            List.of()
        );
    }

    public JournalEntrySummary toSearchSummary(
        JournalEntry entry,
        JournalSearchQuery query
    ) {
        JournalEntrySummary summary = toSummary(entry);
        return new JournalEntrySummary(
            summary.id(),
            summary.type(),
            summary.createdAt(),
            summary.mainItem(),
            summary.itemsCount(),
            summary.totalAmount(),
            summary.paymentStatus(),
            summary.prepaymentAmount(),
            summary.paidAmount(),
            summary.remainingAmount(),
            summary.executionStatus(),
            summary.clientName(),
            summary.clientPhone(),
            summary.fulfillmentMethod(),
            summary.deliveryAddress(),
            summary.factoryReadyDate(),
            summary.factoryReadyAttention(),
            summary.version(),
            searchMatches(entry, query)
        );
    }

    private List<JournalSearchMatch> searchMatches(
        JournalEntry entry,
        JournalSearchQuery query
    ) {
        if (query.isEntryNumber()) {
            return List.of(new JournalSearchMatch(
                JournalSearchMatch.Field.ENTRY_NUMBER,
                (entry.getType() == ru.kamoved.journal.domain.EntryType.ORDER ? "З-" : "П-")
                    + entry.getId(),
                0
            ));
        }

        List<JournalSearchMatch> matches = new ArrayList<>();
        addTextMatch(matches, JournalSearchMatch.Field.NAME,
            entry.getContacts().stream().map(EntryContact::getName).toList(), query);
        addPhoneMatch(matches,
            entry.getContacts().stream().map(EntryContact::getPhone).toList(), query);
        addTextMatch(matches, JournalSearchMatch.Field.ADDRESS,
            List.of(entry.getDeliveryAddress() == null ? "" : entry.getDeliveryAddress()), query);
        addTextMatch(matches, JournalSearchMatch.Field.ITEM,
            entry.getItems().stream().map(JournalEntryItem::getName).toList(), query);
        return matches;
    }

    private void addTextMatch(
        List<JournalSearchMatch> matches,
        JournalSearchMatch.Field field,
        List<String> values,
        JournalSearchQuery query
    ) {
        List<String> matching = values.stream()
            .filter(value -> value != null && query.terms().stream().anyMatch(
                term -> ru.kamoved.journal.domain.JournalSearchNormalizer.normalizeText(value)
                    .contains(term)))
            .toList();
        if (!matching.isEmpty()) {
            matches.add(new JournalSearchMatch(field, matching.getFirst(), matching.size() - 1));
        }
    }

    private void addPhoneMatch(
        List<JournalSearchMatch> matches,
        List<String> values,
        JournalSearchQuery query
    ) {
        List<String> matching = values.stream()
            .filter(value -> value != null && query.terms().stream()
                .filter(term -> term.chars().allMatch(Character::isDigit))
                .anyMatch(term -> {
                    String normalized = ru.kamoved.journal.domain.JournalSearchNormalizer
                        .normalizePhone(value);
                    return normalized != null && normalized.contains(term);
                }))
            .toList();
        if (!matching.isEmpty()) {
            matches.add(new JournalSearchMatch(
                JournalSearchMatch.Field.PHONE,
                matching.getFirst(),
                matching.size() - 1
            ));
        }
    }

    public JournalEntryDetails toDetails(JournalEntry entry) {
        JournalContactDetails client = entry.getContacts().stream()
            .filter(contact -> contact.getType() == ContactType.CLIENT)
            .findFirst()
            .map(this::toContact)
            .orElse(null);

        return new JournalEntryDetails(
            entry.getId(),
            entry.getType(),
            entry.getCreatedAt(),
            entry.getItems().stream().map(this::toItem).toList(),
            entry.getTotalAmount(),
            entry.getPaymentStatus(),
            legacyPrepaymentAmount(entry),
            entry.getPaidAmount(),
            remainingAmount(entry),
            entry.getPayments().stream().map(this::toPayment).toList(),
            entry.getExecutionStatus(),
            client,
            entry.getContacts().stream()
                .filter(contact -> contact.getType() == ContactType.ADDITIONAL)
                .map(this::toContact)
                .toList(),
            entry.getFulfillmentMethod(),
            entry.getDeliveryAddress(),
            entry.getComment(),
            entry.getFactoryReadyDate(),
            entry.isFactoryReadyAttention(),
            entry.getCreatedBy().getDisplayName(),
            entry.getUpdatedAt(),
            entry.getVersion()
        );
    }

    private BigDecimal remainingAmount(JournalEntry entry) {
        return entry.getTotalAmount().subtract(entry.getPaidAmount());
    }

    private BigDecimal legacyPrepaymentAmount(JournalEntry entry) {
        return entry.getPaymentStatus() == PaymentStatus.PREPAID
            ? entry.getPaidAmount()
            : null;
    }

    private PaymentDetails toPayment(JournalPayment payment) {
        return new PaymentDetails(
            payment.getId(),
            payment.getAmount(),
            payment.getPaymentMethod(),
            payment.getComment(),
            payment.getReceivedAt(),
            payment.getCreatedBy().getDisplayName(),
            payment.getCreatedAt(),
            payment.isActive(),
            payment.getVoidedAt(),
            payment.getVoidedBy() == null ? null : payment.getVoidedBy().getDisplayName(),
            payment.getCorrectionOf() == null ? null : payment.getCorrectionOf().getId(),
            payment.getCorrectionReason()
        );
    }

    private JournalContactDetails toContact(EntryContact contact) {
        return new JournalContactDetails(
            contact.getId(),
            contact.getName(),
            contact.getPhone(),
            contact.getComment()
        );
    }

    private JournalItemSummary toItem(JournalEntryItem item) {
        return new JournalItemSummary(
            item.getId(),
            item.getName(),
            item.getQuantity(),
            item.getUnit(),
            item.getUnitPrice(),
            item.getLineTotal()
        );
    }
}
