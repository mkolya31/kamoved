package ru.kamoved.journal.domain;

import org.springframework.stereotype.Component;

@Component
public class PhoneNormalizer {

    public String normalize(String phone) {
        return JournalSearchNormalizer.normalizePhone(phone);
    }
}
