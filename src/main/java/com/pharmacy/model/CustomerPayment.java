package com.pharmacy.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "customer_payments")
public class CustomerPayment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false)
    public Customer customer;

    @ManyToOne(optional = false)
    public CreditTransaction creditTransaction;

    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal amount;

    @Column(nullable = false)
    public LocalDate paymentDate;

    public String paymentMode;
    public String referenceNo;
    public Instant createdAt = Instant.now();

    public CustomerPayment() {}

    public CustomerPayment(Customer customer, CreditTransaction creditTransaction, BigDecimal amount,
                           LocalDate paymentDate, String paymentMode, String referenceNo) {
        this.customer = customer;
        this.creditTransaction = creditTransaction;
        this.amount = amount;
        this.paymentDate = paymentDate;
        this.paymentMode = paymentMode;
        this.referenceNo = referenceNo;
    }
}
