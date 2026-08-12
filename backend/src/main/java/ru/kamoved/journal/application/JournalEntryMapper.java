package ru.kamoved.journal.application;

import org.springframework.stereotype.Component;
import ru.kamoved.journal.api.dto.JournalEntryDetails;
import ru.kamoved.journal.api.dto.JournalEntrySummary;
import ru.kamoved.journal.api.dto.JournalItemSummary;
import ru.kamoved.journal.domain.JournalEntry;
import ru.kamoved.journal.domain.JournalEntryItem;

@Component
public class JournalEntryMapper {

    public JournalEntrySummary toSummary(JournalEntry entry) {
        JournalItemSummary mainItem = entry.getItems().stream()
            .findFirst()
            .map(this::toItem)
            .orElse(null);

        return new JournalEntrySummary(
            entry.getId(),
            entry.getType(),
            entry.getCreatedAt(),
            mainItem,
            entry.getItems().size(),
            entry.getTotalAmount(),
            entry.getPaymentStatus(),
            entry.getExecutionStatus()
        );
    }

    public JournalEntryDetails toDetails(JournalEntry entry) {
        return new JournalEntryDetails(
            entry.getId(),
            entry.getType(),
            entry.getCreatedAt(),
            entry.getItems().stream().map(this::toItem).toList(),
            entry.getTotalAmount(),
            entry.getPaymentStatus(),
            entry.getExecutionStatus()
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
