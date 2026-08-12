package ru.kamoved.journal.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.kamoved.journal.domain.EntryType;
import ru.kamoved.journal.domain.ExecutionStatus;
import ru.kamoved.journal.domain.JournalEntry;

import java.util.Collection;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    Page<JournalEntry> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<JournalEntry> findByTypeAndExecutionStatusInOrderByCreatedAtDesc(
        EntryType type,
        Collection<ExecutionStatus> statuses,
        Pageable pageable
    );
}

