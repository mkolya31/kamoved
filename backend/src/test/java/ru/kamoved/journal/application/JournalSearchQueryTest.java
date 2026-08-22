package ru.kamoved.journal.application;

import org.junit.jupiter.api.Test;
import ru.kamoved.journal.domain.EntryType;

import static org.assertj.core.api.Assertions.assertThat;

class JournalSearchQueryTest {

    @Test
    void recognizesExactEntryNumberVariants() {
        assertThat(JournalSearchQuery.parse("З-123"))
            .extracting(JournalSearchQuery::entryType, JournalSearchQuery::entryId)
            .containsExactly(EntryType.ORDER, 123L);
        assertThat(JournalSearchQuery.parse("п 123"))
            .extracting(JournalSearchQuery::entryType, JournalSearchQuery::entryId)
            .containsExactly(EntryType.SALE, 123L);
        assertThat(JournalSearchQuery.parse("з123").isEntryNumber()).isTrue();
        assertThat(JournalSearchQuery.parse("123").isEntryNumber()).isFalse();
    }

    @Test
    void normalizesWordsAndPhoneQueries() {
        assertThat(JournalSearchQuery.parse("  ГЛАВНОЖ/Фёдор  ").terms())
            .containsExactly("главнож", "федор");
        assertThat(JournalSearchQuery.parse("8 (999) 123-45-67").terms())
            .containsExactly("79991234567");
        assertThat(JournalSearchQuery.parse("8 (999)").terms())
            .containsExactly("7999");
    }
}
