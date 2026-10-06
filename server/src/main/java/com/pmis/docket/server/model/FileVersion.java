package com.pmis.docket.server.model;

import jakarta.persistence.*;
import java.time.Instant;

/** One saved version of a file. The newest one is also what the node points to. */
@Entity
@Table(name = "file_version", indexes = @Index(name = "ix_version_node", columnList = "node_id"))
public class FileVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public Long nodeId;

    public int number;

    @Column(nullable = false, length = 80)
    public String blobKey;

    @Column(length = 20)
    public String ext;

    public long sizeBytes;
    public Instant createdAt;
    public Long createdBy;

    @Column(length = 300)
    public String note;
}
