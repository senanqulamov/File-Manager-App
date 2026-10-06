package com.pmis.docket.server.repo;

import com.pmis.docket.server.model.AuditEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuditRepository extends JpaRepository<AuditEvent, Long> {
    List<AuditEvent> findByNodeIdOrderByOccurredAtDesc(Long nodeId, Pageable page);

    List<AuditEvent> findAllByOrderByOccurredAtDesc(Pageable page);

    List<AuditEvent> findByUserIdAndNodeIdIsNotNullOrderByOccurredAtDesc(Long userId, Pageable page);

    @Query("select e from AuditEvent e where (:kind = '' or e.kind = :kind) and (:q = '' or lower(e.action) like :q "
            + "or lower(e.userLogin) like :q or lower(coalesce(e.nodePath, '')) like :q or lower(coalesce(e.computer, '')) like :q) "
            + "order by e.occurredAt desc")
    List<AuditEvent> search(@Param("kind") String kind, @Param("q") String q, Pageable page);
}
