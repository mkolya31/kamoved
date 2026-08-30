package ru.kamoved.journal.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.kamoved.journal.api.dto.JournalEntryDetails;
import ru.kamoved.journal.api.dto.JournalEntrySummary;
import ru.kamoved.journal.api.dto.JournalPageResponse;
import ru.kamoved.journal.domain.EntryType;
import ru.kamoved.journal.domain.ExecutionStatus;
import ru.kamoved.journal.domain.JournalEntry;
import ru.kamoved.journal.persistence.JournalEntryRepository;
import ru.kamoved.journal.persistence.JournalPaymentRepository;
import ru.kamoved.journal.persistence.JournalSearchRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.EnumSet;

@Service
public class JournalQueryService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Moscow");

    private static final EnumSet<ExecutionStatus> ACTIVE_STATUSES = EnumSet.of(
        ExecutionStatus.NEW,
        ExecutionStatus.ORDERED_FACTORY,
        ExecutionStatus.IN_PRODUCTION,
        ExecutionStatus.READY_FACTORY,
        ExecutionStatus.IN_TRANSIT_TO_WAREHOUSE,
        ExecutionStatus.AT_WAREHOUSE,
        ExecutionStatus.OUT_FOR_DELIVERY
    );

    private final JournalEntryRepository entries;
    private final JournalEntryMapper mapper;
    private final JournalSearchRepository searchRepository;
    private final JournalPaymentRepository payments;
    private final Clock clock;

    public JournalQueryService(
        JournalEntryRepository entries,
        JournalEntryMapper mapper,
        JournalSearchRepository searchRepository,
        JournalPaymentRepository payments,
        Clock clock
    ) {
        this.entries = entries;
        this.mapper = mapper;
        this.searchRepository = searchRepository;
        this.payments = payments;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public JournalPageResponse list(String mode, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<JournalEntry> result = "active".equals(mode)
            ? entries.findActiveOrders(
                EntryType.ORDER, ACTIVE_STATUSES, pageable)
            : entries.findAllByOrderByCreatedAtDesc(pageable);

        return new JournalPageResponse(
            result.getContent().stream().map(mapper::toSummary).toList(),
            result.getNumber(),
            result.getSize(),
            result.hasNext(),
            todayRevenue(),
            result.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public JournalPageResponse search(String query, String mode, int page, int size) {
        JournalSearchQuery parsedQuery = JournalSearchQuery.parse(query);
        if (!parsedQuery.isSearchable()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Введите не менее двух значимых символов"
            );
        }

        JournalSearchRepository.SearchPage result = searchRepository.search(
            parsedQuery,
            "active".equals(mode),
            ACTIVE_STATUSES,
            page,
            size
        );
        return new JournalPageResponse(
            result.items().stream()
                .map(entry -> mapper.toSearchSummary(entry, parsedQuery))
                .toList(),
            page,
            size,
            (long) (page + 1) * size < result.total(),
            todayRevenue(),
            result.total()
        );
    }

    private BigDecimal todayRevenue() {
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        OffsetDateTime from = today.atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
        OffsetDateTime until = today.plusDays(1).atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();

        return payments.sumActivePaymentsReceivedBetween(from, until);
    }

    @Transactional(readOnly = true)
    public JournalEntryDetails get(long id) {
        JournalEntry entry = entries.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Запись не найдена"));
        return mapper.toDetails(entry);
    }
}
