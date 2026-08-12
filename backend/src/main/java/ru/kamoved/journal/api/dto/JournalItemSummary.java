package ru.kamoved.journal.api.dto;

import ru.kamoved.journal.domain.UnitOfMeasure;

import java.math.BigDecimal;

public record JournalItemSummary(
    Long id,
    String name,
    BigDecimal quantity,
    UnitOfMeasure unit,
    BigDecimal unitPrice,
    BigDecimal lineTotal
) {
}

