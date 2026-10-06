package com.pmis.docket.server.repo;

import com.pmis.docket.server.model.AclEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AclRepository extends JpaRepository<AclEntry, Long> {
    List<AclEntry> findByNodeId(Long nodeId);
}
