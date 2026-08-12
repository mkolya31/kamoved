package ru.kamoved.common.error;

import java.util.Map;

public record ApiError(
    String code,
    String message,
    Map<String, String> fields
) {
}

