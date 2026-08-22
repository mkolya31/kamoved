package ru.kamoved.journal.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNormalizerTest {

    private final PhoneNormalizer normalizer = new PhoneNormalizer();

    @Test
    void canonicalizesRussianPrefixesAndKeepsPartialNumbers() {
        assertThat(normalizer.normalize("8 (999) 123-45-67")).isEqualTo("79991234567");
        assertThat(normalizer.normalize("+7 999 123-45-67")).isEqualTo("79991234567");
        assertThat(normalizer.normalize("123-45-67")).isEqualTo("1234567");
    }
}
