package com.pmis.docket.server.service;

import com.pmis.docket.server.model.AuditEvent;
import com.pmis.docket.server.model.Node;
import com.pmis.docket.server.model.User;
import com.pmis.docket.server.repo.AuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuditService {
    private final AuditRepository repo;

    public AuditService(AuditRepository repo) {
        this.repo = repo;
    }

    /** Records an event in its own transaction so it is kept even if the main action fails afterwards. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(User user, String computer, String action, String kind, Node node) {
        record(user, computer, action, kind, node, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(User user, String computer, String action, String kind, Node node, String path) {
        AuditEvent e = new AuditEvent();
        e.occurredAt = Instant.now();
        if (user != null) {
            e.userId = user.id;
            e.userLogin = user.login;
        }
        e.action = action.length() > 300 ? action.substring(0, 300) : action;
        e.kind = kind;
        e.nodeId = node == null ? null : node.id;
        e.nodePath = path;
        e.computer = computer;
        repo.save(e);
    }
}
