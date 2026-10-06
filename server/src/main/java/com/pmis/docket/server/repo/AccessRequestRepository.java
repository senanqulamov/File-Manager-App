package com.pmis.docket.server.repo;

import com.pmis.docket.server.model.AccessRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccessRequestRepository extends JpaRepository<AccessRequest, Long> {
    List<AccessRequest> findAllByOrderByCreatedAtDesc();

    List<AccessRequest> findByNodeIdAndUserIdAndStatus(Long nodeId, Long userId, AccessRequest.Status status);

    long countByStatus(AccessRequest.Status status);
}
