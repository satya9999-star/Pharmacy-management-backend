package com.pharmacy.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "distributor_bills")
public class DistributorBill {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false)
    public Distributor distributor;

    @Column(nullable = false)
    public String billNo;

    @Column(nullable = false)
    public LocalDate billDate;

    public LocalDate dueDate;

    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal totalAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal gstAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal netAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal dueAmount;

    public String status; // "OPEN", "SETTLED"
    public Instant createdAt = Instant.now();

    public DistributorBill() {}

    public DistributorBill(Distributor distributor, String billNo, LocalDate billDate, LocalDate dueDate) {
        this.distributor = distributor;
        this.billNo = billNo;
        this.billDate = billDate;
        this.dueDate = dueDate;
        this.totalAmount = BigDecimal.ZERO.setScale(2);
        this.gstAmount = BigDecimal.ZERO.setScale(2);
        this.netAmount = BigDecimal.ZERO.setScale(2);
        this.paidAmount = BigDecimal.ZERO.setScale(2);
        this.dueAmount = BigDecimal.ZERO.setScale(2);
        this.status = "OPEN";
    }
}
