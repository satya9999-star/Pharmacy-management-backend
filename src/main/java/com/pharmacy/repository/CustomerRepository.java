package com.pharmacy.repository;

import com.pharmacy.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByNameIgnoreCase(String name);
    Optional<Customer> findByMobile(String mobile);
}
