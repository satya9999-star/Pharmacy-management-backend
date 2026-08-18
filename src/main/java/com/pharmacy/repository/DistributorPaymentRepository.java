package com.pharmacy.repository;

import com.pharmacy.model.DistributorPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface DistributorPaymentRepository extends JpaRepository<DistributorPayment, Long> {
    List<DistributorPayment> findByDistributorIdOrderByPaymentDateDesc(Long distributorId);
    List<DistributorPayment> findByDistributorBillId(Long distributorBillId);

    @Query("select coalesce(sum(p.amount), 0) from DistributorPayment p where p.paymentDate between :from and :to")
    BigDecimal paymentsToDistributorsBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
