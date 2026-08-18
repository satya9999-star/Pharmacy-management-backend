package com.pharmacy.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "credit_transactions")
public class CreditTransaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false)
    public Customer customer;

    @OneToOne(optional = false)
    public Sale sale;

    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal creditAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal paidAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal dueAmount;

    public LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    public CreditStatus status;

    public Instant createdAt = Instant.now();

    public CreditTransaction() {}
}
