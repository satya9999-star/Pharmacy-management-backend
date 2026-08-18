package com.pharmacy.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "stock_movements")
public class StockMovement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false)
    public MedicineBatch batch;

    @Enumerated(EnumType.STRING)
    public MovementType movementType;

    public int quantity;
    public Long referenceId;
    public String remarks;
    public Instant createdAt = Instant.now();

    public StockMovement() {}
}
