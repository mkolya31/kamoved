package ru.kamoved.journal.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Repository;
import ru.kamoved.journal.application.JournalSearchQuery;
import ru.kamoved.journal.domain.EntryType;
import ru.kamoved.journal.domain.ExecutionStatus;
import ru.kamoved.journal.domain.JournalEntry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Repository
public class JournalSearchRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public SearchPage search(
        JournalSearchQuery searchQuery,
        boolean activeOnly,
        Collection<ExecutionStatus> activeStatuses,
        int page,
        int size
    ) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<JournalEntry> contentQuery = builder.createQuery(JournalEntry.class);
        Root<JournalEntry> entry = contentQuery.from(JournalEntry.class);
        List<Predicate> predicates = predicates(
            builder, entry, searchQuery, activeOnly, activeStatuses);
        contentQuery.where(predicates.toArray(Predicate[]::new));
        contentQuery.orderBy(
            builder.desc(entry.get("createdAt")),
            builder.desc(entry.get("id"))
        );

        TypedQuery<JournalEntry> pageQuery = entityManager.createQuery(contentQuery);
        pageQuery.setFirstResult(page * size);
        pageQuery.setMaxResults(size);
        List<JournalEntry> items = pageQuery.getResultList();

        CriteriaQuery<Long> countQuery = builder.createQuery(Long.class);
        Root<JournalEntry> countEntry = countQuery.from(JournalEntry.class);
        countQuery.select(builder.count(countEntry));
        countQuery.where(predicates(
            builder, countEntry, searchQuery, activeOnly, activeStatuses
        ).toArray(Predicate[]::new));
        long total = entityManager.createQuery(countQuery).getSingleResult();

        return new SearchPage(items, total);
    }

    private List<Predicate> predicates(
        CriteriaBuilder builder,
        Root<JournalEntry> entry,
        JournalSearchQuery searchQuery,
        boolean activeOnly,
        Collection<ExecutionStatus> activeStatuses
    ) {
        List<Predicate> predicates = new ArrayList<>();
        if (activeOnly) {
            predicates.add(builder.equal(entry.get("type"), EntryType.ORDER));
            predicates.add(entry.get("executionStatus").in(activeStatuses));
        }

        if (searchQuery.isEntryNumber()) {
            predicates.add(builder.equal(entry.get("type"), searchQuery.entryType()));
            predicates.add(builder.equal(entry.get("id"), searchQuery.entryId()));
        } else {
            searchQuery.terms().forEach(term -> predicates.add(
                builder.like(entry.get("searchText"), "%" + term + "%")
            ));
        }
        return predicates;
    }

    public record SearchPage(List<JournalEntry> items, long total) {
    }
}
