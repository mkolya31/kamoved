package ru.kamoved.journal.api.dto;

public record JournalContactDetails(
    Long id,
    String name,
    String phone,
    String comment
) {
}
