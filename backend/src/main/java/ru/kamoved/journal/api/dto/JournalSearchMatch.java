package ru.kamoved.journal.api.dto;

public record JournalSearchMatch(
    Field field,
    String value,
    int additionalCount
) {
    public enum Field {
        ENTRY_NUMBER,
        NAME,
        PHONE,
        ADDRESS,
        ITEM
    }
}
