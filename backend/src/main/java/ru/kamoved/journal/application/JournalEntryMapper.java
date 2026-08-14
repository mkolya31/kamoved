package ru.kamoved.journal.application;

import org.springframework.stereotype.Component;
import ru.kamoved.journal.api.dto.JournalEntryDetails;
import ru.kamoved.journal.api.dto.JournalEntrySummary;
import ru.kamoved.journal.api.dto.JournalContactDetails;
import ru.kamoved.journal.api.dto.JournalItemSummary;
import ru.kamoved.journal.domain.ContactType;
import ru.kamoved.journal.domain.EntryContact;
import ru.kamoved.journal.domain.JournalEntry;
import ru.kamoved.journal.domain.JournalEntryItem;
import ru.kamoved.journal.domain.PaymentStatus;

import java.math.BigDecimal;

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
            entry.getPrepaymentAmount(),
            remainingAmount(entry),
            entry.getExecutionStatus(),
            client == null ? null : client.getName(),
            client == null ? null : client.getPhone(),
            entry.getFulfillmentMethod(),
            entry.getDeliveryAddress(),
            entry.getVersion()
        );
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
            entry.getPrepaymentAmount(),
            remainingAmount(entry),
            entry.getExecutionStatus(),
            client,
            entry.getContacts().stream()
                .filter(contact -> contact.getType() == ContactType.ADDITIONAL)
                .map(this::toContact)
                .toList(),
            entry.getFulfillmentMethod(),
            entry.getDeliveryAddress(),
            entry.getComment(),
            entry.getCreatedBy().getDisplayName(),
            entry.getUpdatedAt(),
            entry.getVersion()
        );
    }

    private BigDecimal remainingAmount(JournalEntry entry) {
        if (entry.getPaymentStatus() == PaymentStatus.PAID) {
            return BigDecimal.ZERO;
        }
        if (entry.getPaymentStatus() == PaymentStatus.PREPAID) {
            return entry.getTotalAmount().subtract(entry.getPrepaymentAmount());
        }
        return entry.getTotalAmount();
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
