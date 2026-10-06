package com.pmis.docket.server.model;

import jakarta.persistence.*;
import java.time.Instant;

/** A root, folder or file. The current file contents live on disk under {@code blobKey}. */
@Entity
@Table(name = "node", indexes = {
        @Index(name = "ix_node_parent", columnList = "parent_id"),
        @Index(name = "ix_node_owner_space", columnList = "owner_id,space"),
        @Index(name = "ix_node_blob", columnList = "blob_key")
})
public class Node {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public Long parentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    public NodeType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    public Space space;

    /** For a PERSONAL root: the user it belongs to. For files and folders: who created it. */
    public Long ownerId;

    @Column(nullable = false, length = 255)
    public String name;

    /** File extension without the dot, lower case. Null for folders. */
    @Column(length = 20)
    public String ext;

    public long sizeBytes;

    public Instant createdAt;
    public Instant modifiedAt;
    public Long modifiedBy;

    @Column(length = 80)
    public String blobKey;

    /** Current version number (files only). */
    @Column(nullable = false, columnDefinition = "integer default 1")
    public int version = 1;

    public boolean deleted;
    public Instant deletedAt;
    public Long deletedBy;
    /** True for the item the user deleted (its children are deleted along with it). */
    @Column(nullable = false, columnDefinition = "boolean default false")
    public boolean deleteRoot;

    public boolean signed;
    public boolean locked;
    @Column(length = 40)
    public String stamp;
    public Long checkedOutBy;
    public Instant checkedOutAt;

    public boolean isFile() { return type == NodeType.FILE; }

    public String fullName() { return ext == null || ext.isBlank() ? name : name + "." + ext; }
}
