package com.pharmacy.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "sale_items")
public class SaleItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false)
    public Sale sale;

    @ManyToOne(optional = false)
    public MedicineBatch batch;

    @Column(nullable = false, precision = 10, scale = 4)
    public BigDecimal quantity;

    @Column(nullable = false, precision = 10, scale = 2)
    public BigDecimal mrp;

    @Column(nullable = false, precision = 10, scale = 2)
    public BigDecimal sellingPrice;

    @Column(nullable = false, precision = 5, scale = 2)
    public BigDecimal gstPercentage;

    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal totalAmount;

    public SaleItem() {}
}
