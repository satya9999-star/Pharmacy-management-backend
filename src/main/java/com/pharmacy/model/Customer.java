package com.pharmacy.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "customers")
public class Customer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public String name;

    public String mobile;
    public String address;

    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal creditLimit = BigDecimal.ZERO;

    public Instant createdAt = Instant.now();

    public Customer() {}

    public Customer(String name, String mobile, String address, BigDecimal creditLimit) {
        this.name = name;
        this.mobile = mobile;
        this.address = address;
        this.creditLimit = creditLimit;
    }
}
