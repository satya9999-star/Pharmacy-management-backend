package com.pharmacy.repository;

import com.pharmacy.model.CustomerPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CustomerPaymentRepository extends JpaRepository<CustomerPayment, Long> {
    List<CustomerPayment> findByCreditTransactionIdOrderByPaymentDateDesc(Long creditTransactionId);
    List<CustomerPayment> findByCustomerIdOrderByPaymentDateDesc(Long customerId);
}
