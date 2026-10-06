package com.pmis.docket.server.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "session_token")
public class SessionToken {
    @Id
    @Column(length = 64)
    public String token;

    @Column(nullable = false)
    public Long userId;

    @Column(nullable = false)
    public Instant expiresAt;

    @Column(length = 100)
    public String computer;
}
