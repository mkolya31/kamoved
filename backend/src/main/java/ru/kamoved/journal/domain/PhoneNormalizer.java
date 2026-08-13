package ru.kamoved.journal.domain;

import org.springframework.stereotype.Component;

@Component
public class PhoneNormalizer {

    public String normalize(String phone) {
        if (phone == null) {
            return null;
        }

        String digits = phone.replaceAll("\\D", "");
        return digits.isEmpty() ? null : digits;
    }
}
