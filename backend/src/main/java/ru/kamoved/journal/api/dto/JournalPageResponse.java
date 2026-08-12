package ru.kamoved.journal.api.dto;

import java.util.List;

public record JournalPageResponse(
    List<JournalEntrySummary> items,
    int page,
    int size,
    boolean hasNext
) {
}

