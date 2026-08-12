package ru.kamoved.journal.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyCalculatorTest {

    private final MoneyCalculator calculator = new MoneyCalculator();

    @Test
    void dropsKopecksInFavorOfClient() {
        BigDecimal result = calculator.calculateLineTotal(
            new BigDecimal("30.1"),
            new BigDecimal("2855")
        );

        assertThat(result).isEqualByComparingTo("85935");
    }

    @Test
    void sumsAlreadyRoundedLineTotals() {
        BigDecimal result = calculator.calculateOrderTotal(List.of(
            new BigDecimal("85935"),
            new BigDecimal("4690")
        ));

        assertThat(result).isEqualByComparingTo("90625");
    }
}

