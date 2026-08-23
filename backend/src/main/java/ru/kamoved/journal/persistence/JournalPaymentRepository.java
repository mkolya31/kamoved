package ru.kamoved.journal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.kamoved.journal.domain.JournalPayment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public interface JournalPaymentRepository extends JpaRepository<JournalPayment, Long> {

    @Query("""
        select coalesce(sum(payment.amount), 0)
        from JournalPayment payment
        where payment.voidedAt is null
          and payment.receivedAt >= :from
          and payment.receivedAt < :until
        """)
    BigDecimal sumActivePaymentsReceivedBetween(
        @Param("from") OffsetDateTime from,
        @Param("until") OffsetDateTime until
    );
}
