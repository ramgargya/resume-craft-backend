package com.resumebuilder.backend.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users", uniqueConstraints = {
    @UniqueConstraint(columnNames = "email")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    private String name;

    private String password; // Will contain hashed password or null for GOOGLE login

    @Column(nullable = false)
    private String provider; // "LOCAL" or "GOOGLE"

    @Builder.Default
    @Column(nullable = true)
    private String role = "USER"; // "USER" or "ADMIN"

    @Builder.Default
    @Column(nullable = true)
    private String subscriptionTier = "FREE"; // "FREE" or "PAID"

    public String getRole() {
        return role == null ? "USER" : role;
    }

    public String getSubscriptionTier() {
        return subscriptionTier == null ? "FREE" : subscriptionTier;
    }

    @Builder.Default
    @Column(nullable = true)
    private Boolean verified = false;

    private String otp;

    private java.time.LocalDateTime otpExpiry;

    public Boolean getVerified() {
        return verified == null ? false : verified;
    }
}
