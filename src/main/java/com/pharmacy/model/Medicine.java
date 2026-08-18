package com.pharmacy.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "medicines")
public class Medicine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true)
    public String code;

    @Column(nullable = false)
    public String name;

    public String genericName;
    public String manufacturer;
    public String category;
    public String hsnCode;

    @Column(length = 1000)
    public String sideEffects;

    @Column(nullable = false, precision = 5, scale = 2)
    public BigDecimal gstPercentage;

    @Column(nullable = false, precision = 10, scale = 2)
    public BigDecimal mrp;

    @Column(nullable = false, precision = 10, scale = 2)
    public BigDecimal sellingPrice;

    public boolean prescriptionRequired;

    @Column(nullable = false)
    public Integer stockWatchQty = 10;

    @Column(name = "order_status")
    public String orderStatus = "Low Stock";

    @Column(name = "ordered_date")
    public LocalDate orderedDate;

    @Column(name = "ordered_distributor_id")
    public Long orderedDistributorId;

    @Column(name = "ordered_distributor_name")
    public String orderedDistributorName;

    @Column(name = "ordered_quantity")
    public Integer orderedQuantity;

    public Instant createdAt = Instant.now();

    public Medicine() {}

    public Medicine(String code, String name, String genericName, String manufacturer, String category,
                    String hsnCode, BigDecimal gstPercentage, BigDecimal mrp, BigDecimal sellingPrice,
                    boolean prescriptionRequired) {
        this(code, name, genericName, manufacturer, category, hsnCode, gstPercentage, mrp, sellingPrice, prescriptionRequired, 10, null);
    }

    public Medicine(String code, String name, String genericName, String manufacturer, String category,
                    String hsnCode, BigDecimal gstPercentage, BigDecimal mrp, BigDecimal sellingPrice,
                    boolean prescriptionRequired, Integer stockWatchQty) {
        this(code, name, genericName, manufacturer, category, hsnCode, gstPercentage, mrp, sellingPrice, prescriptionRequired, stockWatchQty, null);
    }

    public Medicine(String code, String name, String genericName, String manufacturer, String category,
                    String hsnCode, BigDecimal gstPercentage, BigDecimal mrp, BigDecimal sellingPrice,
                    boolean prescriptionRequired, Integer stockWatchQty, String sideEffects) {
        this.code = code;
        this.name = name;
        this.genericName = genericName;
        this.manufacturer = manufacturer;
        this.category = category;
        this.hsnCode = hsnCode;
        this.gstPercentage = gstPercentage;
        this.mrp = mrp;
        this.sellingPrice = sellingPrice;
        this.prescriptionRequired = prescriptionRequired;
        this.stockWatchQty = stockWatchQty != null ? stockWatchQty : 10;
        this.sideEffects = sideEffects;
    }
}
