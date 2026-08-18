package com.pharmacy.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
public class UserAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true)
    public String username;

    @Column(nullable = false)
    public String password;

    @Column(nullable = false)
    public String fullName;

    public String mobile;
    public String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Role role;

    public boolean active = true;
    public Instant createdAt = Instant.now();

    @Column(name = "password_reset_required", nullable = false)
    public boolean passwordResetRequired = false;

    @Column(name = "temp_pwd_expiry")
    public Instant temporaryPasswordExpiry;

    @Column(name = "password_history", length = 1000)
    public String passwordHistory;

    public UserAccount() {}

    public UserAccount(String username, String password, String fullName, Role role) {
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.role = role;
    }
}
