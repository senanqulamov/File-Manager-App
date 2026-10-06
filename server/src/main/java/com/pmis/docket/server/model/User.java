package com.pmis.docket.server.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "app_user")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 100)
    public String login;

    @Column(nullable = false, length = 200)
    public String displayName;

    @Column(length = 100)
    public String department;

    /** Permission group key, e.g. "finance", "hr". Everyone is also in "all". */
    @Column(length = 60)
    public String groupKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public Role role = Role.USER;

    public long quotaBytes = 20L * 1024 * 1024 * 1024;

    public boolean active = true;

    /** Set after IT creates the account or resets the password. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    public boolean mustChangePassword;

    @Column(nullable = false, length = 100)
    public String passwordHash;

    public Instant lastSignIn;

    @Column(length = 100)
    public String lastComputer;

    public String initials() {
        String[] p = displayName.trim().split("\\s+");
        String a = p[0].substring(0, 1);
        String b = p.length > 1 ? p[1].substring(0, 1) : "";
        return (a + b).toUpperCase();
    }

    public boolean isAdmin() { return role == Role.ADMIN; }
}
