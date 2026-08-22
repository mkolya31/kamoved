package ru.kamoved.journal.application;

import ru.kamoved.journal.domain.EntryType;
import ru.kamoved.journal.domain.JournalSearchNormalizer;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record JournalSearchQuery(
    String original,
    List<String> terms,
    EntryType entryType,
    Long entryId
) {

    private static final Pattern ENTRY_NUMBER = Pattern.compile(
        "(?iu)^\\s*([зп])\\s*[-./]?\\s*(\\d+)\\s*$"
    );
    private static final Pattern PHONE_QUERY = Pattern.compile("^[+\\d\\s()\\-./]+$");

    public static JournalSearchQuery parse(String rawQuery) {
        String original = rawQuery == null ? "" : rawQuery.trim();
        Matcher entryNumber = ENTRY_NUMBER.matcher(original);
        if (entryNumber.matches()) {
            EntryType type = "з".equalsIgnoreCase(entryNumber.group(1))
                ? EntryType.ORDER
                : EntryType.SALE;
            try {
                return new JournalSearchQuery(
                    original,
                    List.of(),
                    type,
                    Long.parseLong(entryNumber.group(2))
                );
            } catch (NumberFormatException ignored) {
                return new JournalSearchQuery(original, List.of(), null, null);
            }
        }

        if (PHONE_QUERY.matcher(original).matches()) {
            String phone = JournalSearchNormalizer.normalizePhone(original);
            if (phone != null && phone.startsWith("8")
                && original.stripLeading().matches("^8[\\s(].*")) {
                phone = "7" + phone.substring(1);
            }
            return new JournalSearchQuery(
                original,
                phone == null ? List.of() : List.of(phone),
                null,
                null
            );
        }

        String normalized = JournalSearchNormalizer.normalizeText(original);
        List<String> terms = normalized.isEmpty()
            ? List.of()
            : Arrays.stream(normalized.split(" ")).distinct().toList();
        return new JournalSearchQuery(original, terms, null, null);
    }

    public boolean isEntryNumber() {
        return entryType != null && entryId != null;
    }

    public boolean isSearchable() {
        if (original.codePoints().filter(Character::isLetterOrDigit).count() < 2) {
            return false;
        }
        return isEntryNumber() || !terms.isEmpty();
    }
}
