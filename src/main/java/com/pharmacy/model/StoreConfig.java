package com.pharmacy.model;

import jakarta.persistence.*;

@Entity
@Table(name = "store_config")
public class StoreConfig {
    @Id
    public Long id = 1L;

    @Column(nullable = false)
    public String name;

    @Column(nullable = false)
    public String addressLine1;

    @Column(nullable = false)
    public String addressLine2;

    @Column(nullable = false)
    public String phone;

    @Column(nullable = false)
    public String drugLicense22;

    @Column(nullable = false)
    public String drugLicense21;

    @Column(nullable = false)
    public String gstNumber;

    @Column(nullable = false)
    public boolean enableAutoReminders = true;

    @Column(nullable = false)
    public int reminderDays = 3;

    public String whatsappGatewayUrl;
    public String whatsappToken;
    public String whatsappSender;

    @Column(nullable = false)
    public String dailyReminderTime = "09:00";

    public StoreConfig() {}

    public StoreConfig(String name, String addressLine1, String addressLine2, String phone,
                       String drugLicense22, String drugLicense21, String gstNumber,
                       boolean enableAutoReminders, int reminderDays,
                       String whatsappGatewayUrl, String whatsappToken, String whatsappSender,
                       String dailyReminderTime) {
        this.name = name;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.phone = phone;
        this.drugLicense22 = drugLicense22;
        this.drugLicense21 = drugLicense21;
        this.gstNumber = gstNumber;
        this.enableAutoReminders = enableAutoReminders;
        this.reminderDays = reminderDays;
        this.whatsappGatewayUrl = whatsappGatewayUrl;
        this.whatsappToken = whatsappToken;
        this.whatsappSender = whatsappSender;
        this.dailyReminderTime = dailyReminderTime != null ? dailyReminderTime : "09:00";
    }
}
