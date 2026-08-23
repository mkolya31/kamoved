package ru.kamoved.journal.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.kamoved.journal.domain.EntryType;
import ru.kamoved.journal.domain.ExecutionStatus;
import ru.kamoved.journal.domain.JournalEntry;
import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.Optional;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    Page<JournalEntry> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<JournalEntry> findByTypeAndExecutionStatusInOrderByCreatedAtDesc(
        EntryType type,
        Collection<ExecutionStatus> statuses,
        Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select entry from JournalEntry entry where entry.id = :id")
    Optional<JournalEntry> findByIdForUpdate(@Param("id") long id);
}
