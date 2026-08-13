package ru.kamoved.journal.api.dto;

import jakarta.validation.constraints.Size;

public record ContactRequest(
    @Size(max = 255)
    String name,

    @Size(max = 100)
    String phone,

    @Size(max = 2000)
    String comment
) {
}
