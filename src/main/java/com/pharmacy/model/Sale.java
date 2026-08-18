package com.pharmacy.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "sales")
public class Sale {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true)
    public String billNo;

    @ManyToOne
    public Customer customer;

    public String customerAge;
    public String doctorName;

    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal totalAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal discountAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal gstAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal roundingAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal netAmount;

    @Enumerated(EnumType.STRING)
    public PaymentMode paymentMode;

    @Enumerated(EnumType.STRING)
    public PaymentStatus paymentStatus;

    @ManyToOne(optional = false)
    public UserAccount createdBy;

    public Instant createdAt = Instant.now();

    public Sale() {}
}
