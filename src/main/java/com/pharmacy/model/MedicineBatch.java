package com.pharmacy.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "medicine_batches")
public class MedicineBatch {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false)
    public Medicine medicine;

    @Column(nullable = false)
    public String batchNo;

    public LocalDate manufactureDate;

    @Column(nullable = false)
    public LocalDate expiryDate;

    @Column(nullable = false, precision = 10, scale = 2)
    public BigDecimal purchasePrice;

    @Column(nullable = false, precision = 10, scale = 2)
    public BigDecimal sellingPrice;

    public int quantity;
    public int availableQuantity;
    // Tracks loose (partial-strip) unit count separately for accurate sub-strip selling
    public int looseUnitsAvailable = 0;

    @Column(precision = 10, scale = 2)
    public BigDecimal mrp;

    @ManyToOne
    public Distributor distributor;

    @ManyToOne
    public DistributorBill distributorBill;

    public Integer free;
    public BigDecimal discountPercentage;

    public Instant createdAt = Instant.now();

    public MedicineBatch() {}

    public MedicineBatch(Medicine medicine, String batchNo, LocalDate expiryDate, BigDecimal purchasePrice,
                          BigDecimal sellingPrice, int quantity, Distributor distributor) {
        this.medicine = medicine;
        this.batchNo = batchNo;
        this.expiryDate = expiryDate;
        this.purchasePrice = purchasePrice;
        this.sellingPrice = sellingPrice;
        this.mrp = medicine != null ? medicine.mrp : null;
        this.quantity = quantity;
        this.availableQuantity = quantity;
        this.distributor = distributor;
    }
}
