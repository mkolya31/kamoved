package ru.kamoved.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected AppUser() {
    }

    public AppUser(String username, String passwordHash, String displayName) {
        this(username, passwordHash, displayName, true);
    }

    public AppUser(String username, String passwordHash, String displayName, boolean active) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.active = active;
        this.createdAt = OffsetDateTime.now();
    }

    public void synchronizeFromConfiguration(
        String passwordHash,
        String displayName,
        boolean active
    ) {
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isActive() {
        return active;
    }
}
