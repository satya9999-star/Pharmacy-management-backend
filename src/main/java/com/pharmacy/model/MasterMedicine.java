package com.pharmacy.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "master_medicines", indexes = {
    @Index(name = "idx_master_med_name", columnList = "name"),
    @Index(name = "idx_master_med_salt", columnList = "salt_composition")
})
public class MasterMedicine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 500)
    public String name;

    @Column(name = "salt_composition", length = 500)
    public String saltComposition;

    @Column(name = "medicine_desc", columnDefinition = "TEXT")
    public String medicineDesc;

    @Column(name = "side_effects", columnDefinition = "TEXT")
    public String sideEffects;

    @Column(name = "drug_interactions", columnDefinition = "TEXT")
    public String drugInteractions;

    @Column(name = "manufacturer_name", length = 500)
    public String manufacturerName;

    public String category;
    public BigDecimal price;
    public String packSizeLabel;
    public boolean discontinued;

    public MasterMedicine() {}

    public MasterMedicine(String name, String saltComposition, String medicineDesc, String sideEffects, String drugInteractions,
                          String manufacturerName, String category, BigDecimal price, String packSizeLabel, boolean discontinued) {
        this.name = name;
        this.saltComposition = saltComposition;
        this.medicineDesc = medicineDesc;
        this.sideEffects = sideEffects;
        this.drugInteractions = drugInteractions;
        this.manufacturerName = manufacturerName;
        this.category = category;
        this.price = price;
        this.packSizeLabel = packSizeLabel;
        this.discontinued = discontinued;
    }
}
