package com.pharmacy.repository;

import com.pharmacy.model.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long> {
    Optional<Sale> findByBillNo(String billNo);
    long countByCreatedAtBetween(Instant from, Instant to);
    List<Sale> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    List<Sale> findByCustomerIsNullOrderByCreatedAtDesc();

    @Query("select coalesce(sum(s.netAmount), 0) from Sale s where s.createdAt between :from and :to")
    BigDecimal revenueBetween(@Param("from") Instant from, @Param("to") Instant to);

    List<Sale> findByCreatedAtBetweenOrderByCreatedAtAsc(Instant from, Instant to);
    Optional<Sale> findFirstByOrderByCreatedAtAsc();
}
