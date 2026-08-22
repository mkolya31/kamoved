package ru.kamoved.journal.domain;

import java.util.Locale;
import java.util.regex.Pattern;

public final class JournalSearchNormalizer {

    private static final Pattern SEPARATORS = Pattern.compile("[^\\p{L}\\p{N}]+");

    private JournalSearchNormalizer() {
    }

    public static String normalizeText(String value) {
        if (value == null) {
            return "";
        }

        return SEPARATORS.matcher(value.toLowerCase(Locale.ROOT).replace('ё', 'е'))
            .replaceAll(" ")
            .trim()
            .replaceAll(" +", " ");
    }

    public static String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }

        String digits = phone.replaceAll("\\D", "");
        if (digits.length() == 11 && digits.charAt(0) == '8') {
            digits = "7" + digits.substring(1);
        }
        return digits.isEmpty() ? null : digits;
    }
}
