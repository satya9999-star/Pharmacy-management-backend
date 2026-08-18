package com.pharmacy.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "activity_logs")
public class ActivityLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public String action; // "SALE_CREATED", "MEDICINE_ADDED", "CREDIT_COLLECTED", etc.

    @Column(nullable = false)
    public String performedBy;

    public String details;
    public String entityType; // "Sale", "Medicine", "Credit", etc.
    public Long entityId;
    public Instant createdAt = Instant.now();

    public ActivityLog() {}

    public ActivityLog(String action, String performedBy, String details, String entityType, Long entityId) {
        this.action = action;
        this.performedBy = performedBy;
        this.details = details;
        this.entityType = entityType;
        this.entityId = entityId;
    }
}
