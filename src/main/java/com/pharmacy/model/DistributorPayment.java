package com.pharmacy.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "distributor_payments")
public class DistributorPayment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false)
    public Distributor distributor;

    @ManyToOne
    public DistributorBill distributorBill;

    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal amount;

    @Column(nullable = false)
    public LocalDate paymentDate;

    public String paymentMode;
    public String referenceNo;
    public Instant createdAt = Instant.now();

    public DistributorPayment() {}

    public DistributorPayment(Distributor distributor, DistributorBill distributorBill, BigDecimal amount,
                               LocalDate paymentDate, String paymentMode, String referenceNo) {
        this.distributor = distributor;
        this.distributorBill = distributorBill;
        this.amount = amount;
        this.paymentDate = paymentDate;
        this.paymentMode = paymentMode;
        this.referenceNo = referenceNo;
    }
}
