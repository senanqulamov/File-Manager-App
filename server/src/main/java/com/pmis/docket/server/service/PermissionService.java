package com.pmis.docket.server.service;

import com.pmis.docket.server.model.*;
import com.pmis.docket.server.repo.AclRepository;
import com.pmis.docket.server.repo.NodeRepository;
import com.pmis.docket.server.repo.ShareRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Decides what a user may do with a node.
 *
 * PERSONAL space: the owner has full control. Others only get what the owner shared with them
 * (on the item itself or on a folder above it): "can view" = read, "can edit" = read & write.
 * COMPANY space: IT administrators have full control. For everyone else we walk up from the node
 * and use the first folder that has permission lines: a line for the user wins; otherwise the best
 * of their group line and the "all" line. The company root itself is read-only.
 */
@Service
public class PermissionService {
    private final NodeRepository nodes;
    private final AclRepository acls;
    private final ShareRepository shares;

    public PermissionService(NodeRepository nodes, AclRepository acls, ShareRepository shares) {
        this.nodes = nodes;
        this.acls = acls;
        this.shares = shares;
    }

    public Access effective(User user, Node node) {
        if (node.space == Space.PERSONAL) {
            Node root = personalRoot(node);
            if (root != null && user.id.equals(root.ownerId)) return Access.FULL;
            return sharedAccess(user, node);
        }
        if (user.isAdmin()) {
            return node.type == NodeType.ROOT ? Access.READ : Access.FULL;
        }
        Node n = node;
        while (n != null) {
            if (n.type == NodeType.ROOT) return Access.READ;
            Access a = fromAcl(user, acls.findByNodeId(n.id));
            if (a != null) return a;
            n = parent(n);
        }
        return Access.NONE;
    }

    /** True if names/places inside this folder may change (create, upload, paste, rename/delete children). */
    public boolean canWriteInside(User user, Node folder) {
        if (folder.type == NodeType.ROOT && folder.space == Space.COMPANY) return user.isAdmin();
        return effective(user, folder).atLeast(Access.WRITE);
    }

    /** True if the user owns the personal space this node is in. */
    public boolean ownsPersonal(User user, Node node) {
        if (node.space != Space.PERSONAL) return false;
        Node root = personalRoot(node);
        return root != null && user.id.equals(root.ownerId);
    }

    /** The topmost item shared with this user on the way from the node up (null if none). */
    public Node sharedTop(User user, Node node) {
        Node top = null;
        Instant now = Instant.now();
        Node n = node;
        while (n != null && n.type != NodeType.ROOT) {
            for (Share s : shares.findByNodeIdAndWithUserId(n.id, user.id)) {
                if (s.active(now)) top = n;
            }
            n = parent(n);
        }
        return top;
    }

    public Node personalRoot(Node node) {
        Node n = node;
        while (n != null && n.type != NodeType.ROOT) n = parent(n);
        return n;
    }

    private Access sharedAccess(User user, Node node) {
        Access best = Access.NONE;
        Instant now = Instant.now();
        Node n = node;
        while (n != null && n.type != NodeType.ROOT) {
            for (Share s : shares.findByNodeIdAndWithUserId(n.id, user.id)) {
                if (s.active(now)) best = Access.max(best, s.canEdit ? Access.WRITE : Access.READ);
            }
            n = parent(n);
        }
        return best;
    }

    private Access fromAcl(User user, List<AclEntry> entries) {
        if (entries.isEmpty()) return null;
        Access userLine = null, group = null, all = null;
        for (AclEntry e : entries) {
            if (e.principal.equals("user:" + user.id)) userLine = e.access;
            else if (user.groupKey != null && e.principal.equals("group:" + user.groupKey)) group = e.access;
            else if (e.principal.equals("all")) all = e.access;
        }
        if (userLine != null) return userLine;
        if (group == null && all == null) return null;
        if (group == null) return all;
        if (all == null) return group;
        return Access.max(group, all);
    }

    private Node parent(Node n) {
        return n.parentId == null ? null : nodes.findById(n.parentId).orElse(null);
    }
}
