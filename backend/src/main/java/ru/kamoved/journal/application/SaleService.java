package ru.kamoved.journal.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kamoved.auth.domain.AppUser;
import ru.kamoved.auth.persistence.AppUserRepository;
import ru.kamoved.journal.api.dto.CreateSaleRequest;
import ru.kamoved.journal.api.dto.JournalEntrySummary;
import ru.kamoved.journal.domain.JournalEntry;
import ru.kamoved.journal.domain.JournalEntryItem;
import ru.kamoved.journal.domain.JournalPayment;
import ru.kamoved.journal.domain.MoneyCalculator;
import ru.kamoved.journal.persistence.JournalEntryRepository;

import java.math.BigDecimal;

@Service
public class SaleService {

    private final AppUserRepository users;
    private final JournalEntryRepository entries;
    private final MoneyCalculator moneyCalculator;
    private final JournalEntryMapper mapper;

    public SaleService(
        AppUserRepository users,
        JournalEntryRepository entries,
        MoneyCalculator moneyCalculator,
        JournalEntryMapper mapper
    ) {
        this.users = users;
        this.entries = entries;
        this.moneyCalculator = moneyCalculator;
        this.mapper = mapper;
    }

    @Transactional
    public JournalEntrySummary create(CreateSaleRequest request, String username) {
        AppUser creator = users.findByUsernameIgnoreCase(username).orElseThrow();
        JournalEntry sale = JournalEntry.sale(creator, trimToNull(request.comment()));

        request.items().forEach(requestItem -> {
            BigDecimal lineTotal = moneyCalculator.calculateLineTotal(
                requestItem.quantity(), requestItem.unitPrice()
            );
            sale.addItem(new JournalEntryItem(
                requestItem.catalogProductId(),
                requestItem.name().trim(),
                requestItem.quantity(),
                requestItem.effectiveUnit(),
                requestItem.unitPrice(),
                lineTotal
            ));
        });

        sale.setTotalAmount(moneyCalculator.calculateOrderTotal(
            sale.getItems().stream().map(JournalEntryItem::getLineTotal).toList()
        ));
        if (sale.getTotalAmount().signum() <= 0) {
            throw new InvalidPaymentException("Сумма продажи должна быть больше нуля");
        }
        sale.addPayment(JournalPayment.received(
            sale.getTotalAmount(),
            request.paymentMethod(),
            trimToNull(request.paymentComment()),
            creator
        ));
        sale.refreshSearchText();

        return mapper.toSummary(entries.saveAndFlush(sale));
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
