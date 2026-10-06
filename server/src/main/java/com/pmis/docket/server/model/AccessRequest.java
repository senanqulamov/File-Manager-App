package com.pmis.docket.server.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "access_request")
public class AccessRequest {
    public enum Status { PENDING, APPROVED, DENIED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public Long nodeId;

    @Column(nullable = false)
    public Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    public Access level = Access.READ;

    @Column(length = 300)
    public String note;

    public Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    public Status status = Status.PENDING;

    public Long decidedBy;
    public Instant decidedAt;
}
