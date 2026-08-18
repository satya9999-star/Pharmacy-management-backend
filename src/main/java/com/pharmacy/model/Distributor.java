package com.pharmacy.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "distributors")
public class Distributor {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public String name;

    public String contactPerson;
    public String mobile;
    public String email;
    public String gstNumber;
    public String address;
    public String upiId;
    public String bankName;
    public String bankAccountNo;
    public String bankIfscCode;

    public Instant createdAt = Instant.now();

    public Distributor() {}

    public Distributor(String name, String contactPerson, String mobile, String gstNumber, String address, String upiId) {
        this.name = name;
        this.contactPerson = contactPerson;
        this.mobile = mobile;
        this.gstNumber = gstNumber;
        this.address = address;
        this.upiId = upiId;
    }
}
