package com.pmis.docket.server.model;

import jakarta.persistence.*;

/**
 * One permission line on a company folder.
 * principal = "all" (everyone), "group:finance", or "user:12".
 */
@Entity
@Table(name = "acl_entry", uniqueConstraints = @UniqueConstraint(columnNames = {"node_id", "principal"}))
public class AclEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public Long nodeId;

    @Column(nullable = false, length = 80)
    public String principal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    public Access access;

    public AclEntry() { }

    public AclEntry(Long nodeId, String principal, Access access) {
        this.nodeId = nodeId;
        this.principal = principal;
        this.access = access;
    }
}
