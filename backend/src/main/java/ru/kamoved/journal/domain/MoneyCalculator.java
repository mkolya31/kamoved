package ru.kamoved.journal.domain;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

@Component
public class MoneyCalculator {

    public BigDecimal calculateLineTotal(BigDecimal quantity, BigDecimal unitPrice) {
        return quantity.multiply(unitPrice).setScale(0, RoundingMode.FLOOR);
    }

    public BigDecimal calculateOrderTotal(Collection<BigDecimal> lineTotals) {
        return lineTotals.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

