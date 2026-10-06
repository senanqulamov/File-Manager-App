package com.pmis.docket.server.model;

import jakarta.persistence.*;
import java.time.Instant;

/** Append-only record of who did what. Never updated or deleted by the application. */
@Entity
@Table(name = "audit_event", indexes = {
        @Index(name = "ix_audit_at", columnList = "occurred_at"),
        @Index(name = "ix_audit_node", columnList = "node_id")
})
public class AuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public Instant occurredAt;

    public Long userId;

    @Column(length = 100)
    public String userLogin;

    /** Human-readable action, e.g. "Uploaded", "Renamed from “Old”". */
    @Column(nullable = false, length = 300)
    public String action;

    /** Category for filtering: Opened, Edited, Shared, Deleted, Permissions, Sign-in. */
    @Column(nullable = false, length = 20)
    public String kind;

    public Long nodeId;

    @Column(length = 1000)
    public String nodePath;

    @Column(length = 100)
    public String computer;
}
