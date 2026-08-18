package com.pharmacy.repository;

import com.pharmacy.model.DistributorBill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DistributorBillRepository extends JpaRepository<DistributorBill, Long> {
    List<DistributorBill> findByDistributorIdOrderByBillDateDesc(Long distributorId);
    Optional<DistributorBill> findByDistributorIdAndBillNo(Long distributorId, String billNo);

    @Query("select coalesce(sum(b.netAmount), 0) from DistributorBill b where b.billDate between :from and :to")
    BigDecimal purchasesBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select coalesce(sum(b.dueAmount), 0) from DistributorBill b")
    BigDecimal totalDueForDistributors();
}
