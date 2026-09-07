package ru.kamoved.journal.application;

import org.springframework.stereotype.Service;
import ru.kamoved.journal.domain.JournalEntry;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

@Service
public class EntryDateService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Moscow");
    private final Clock clock;

    public EntryDateService(Clock clock) {
        this.clock = clock;
    }

    public void initialize(JournalEntry entry, boolean backdated, String requestedDate) {
        OffsetDateTime now = OffsetDateTime.now(clock.withZone(BUSINESS_ZONE));
        LocalDate date = now.toLocalDate();
        if (backdated) {
            try {
                date = LocalDate.parse(requestedDate == null ? "" : requestedDate);
            } catch (DateTimeParseException exception) {
                throw new InvalidOrderException("Укажите существующую дату заказа или продажи");
            }
            if (date.getYear() < 1 || !date.isBefore(now.toLocalDate())) {
                throw new InvalidOrderException("Дата заказа или продажи должна быть раньше сегодняшней по Москве");
            }
        }
        entry.initializeCreation(date, now);
    }
}
