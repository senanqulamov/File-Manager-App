package com.pmis.docket.server.model;

import jakarta.persistence.*;
import java.time.Instant;

/** An item from someone's My files shared with one colleague. */
@Entity
@Table(name = "share", indexes = {
        @Index(name = "ix_share_node", columnList = "node_id"),
        @Index(name = "ix_share_with", columnList = "with_user_id")
})
public class Share {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public Long nodeId;

    @Column(nullable = false)
    public Long ownerId;

    @Column(nullable = false)
    public Long withUserId;

    /** true = can edit, false = can view. */
    public boolean canEdit;

    public Instant createdAt;

    /** null = never expires. */
    public Instant expiresAt;

    public boolean active(Instant now) { return expiresAt == null || expiresAt.isAfter(now); }
}
