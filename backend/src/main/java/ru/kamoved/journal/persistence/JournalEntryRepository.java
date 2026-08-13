package ru.kamoved.journal.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.kamoved.journal.domain.EntryType;
import ru.kamoved.journal.domain.ExecutionStatus;
import ru.kamoved.journal.domain.JournalEntry;
import ru.kamoved.journal.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    Page<JournalEntry> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<JournalEntry> findByTypeAndExecutionStatusInOrderByCreatedAtDesc(
        EntryType type,
        Collection<ExecutionStatus> statuses,
        Pageable pageable
    );

    @Query("""
        select coalesce(sum(entry.totalAmount), 0)
        from JournalEntry entry
        where lower(entry.createdBy.username) = lower(:username)
          and entry.type = :type
          and entry.paymentStatus = :paymentStatus
          and entry.executionStatus = :executionStatus
          and entry.createdAt >= :from
          and entry.createdAt < :until
        """)
    BigDecimal sumRevenueBySellerAndCreatedAt(
        @Param("username") String username,
        @Param("type") EntryType type,
        @Param("paymentStatus") PaymentStatus paymentStatus,
        @Param("executionStatus") ExecutionStatus executionStatus,
        @Param("from") OffsetDateTime from,
        @Param("until") OffsetDateTime until
    );
}
