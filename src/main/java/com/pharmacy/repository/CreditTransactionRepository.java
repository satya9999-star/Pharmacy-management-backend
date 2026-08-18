package com.pharmacy.repository;

import com.pharmacy.model.CreditStatus;
import com.pharmacy.model.CreditTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface CreditTransactionRepository extends JpaRepository<CreditTransaction, Long> {
    List<CreditTransaction> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    List<CreditTransaction> findByStatusOrderByDueDateAsc(CreditStatus status);
    List<CreditTransaction> findByStatusOrderByCreatedAtDesc(CreditStatus status);
    List<CreditTransaction> findAllByOrderByCreatedAtDesc();

    @Query("select coalesce(sum(c.dueAmount), 0) from CreditTransaction c where c.customer.id = :customerId and c.status = 'OPEN'")
    BigDecimal outstandingForCustomer(@Param("customerId") Long customerId);

    @Query("select coalesce(sum(c.dueAmount), 0) from CreditTransaction c where c.status = 'OPEN'")
    BigDecimal totalOutstanding();

    @Query("select coalesce(sum(c.creditAmount), 0) from CreditTransaction c where c.createdAt between :from and :to")
    BigDecimal creditAmountBetween(@Param("from") Instant from, @Param("to") Instant to);
}
