package ru.kamoved.journal.application;

import org.junit.jupiter.api.Test;
import ru.kamoved.journal.domain.JournalEntry;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.*;

class EntryDateServiceTest {
    @Test
    void usesMoscowDayAtCreationAndIgnoresDateWhenUnchecked() {
        Instant now = Instant.parse("2026-09-06T21:00:00Z");
        var service = new EntryDateService(Clock.fixed(now, ZoneOffset.UTC));
        JournalEntry entry = JournalEntry.sale(null, null);
        service.initialize(entry, false, "2020-01-01");
        assertThat(entry.getEntryDate().toString()).isEqualTo("2026-09-07");
        assertThat(entry.getCreatedAt().toInstant()).isEqualTo(now);
        assertThat(entry.getInitialPaymentReceivedAt().toInstant()).isEqualTo(now);
        assertThatThrownBy(() -> service.initialize(entry, true, "2020-01-01"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validatesAgainstMoscowTodayEvenWhenUtcIsStillYesterday() {
        var service = new EntryDateService(Clock.fixed(Instant.parse("2026-09-06T21:00:00Z"), ZoneOffset.UTC));
        JournalEntry entry = JournalEntry.sale(null, null);
        service.initialize(entry, true, "2026-09-06");
        assertThat(entry.getEntryDate().toString()).isEqualTo("2026-09-06");
        assertThat(entry.getInitialPaymentReceivedAt().toString()).isEqualTo("2026-09-06T00:00+03:00");
        assertThatThrownBy(() -> service.initialize(JournalEntry.sale(null, null), true, "2026-09-07"))
            .isInstanceOf(InvalidOrderException.class);
    }
}
