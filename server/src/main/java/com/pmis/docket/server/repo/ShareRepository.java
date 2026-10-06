package com.pmis.docket.server.repo;

import com.pmis.docket.server.model.Share;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShareRepository extends JpaRepository<Share, Long> {
    List<Share> findByNodeId(Long nodeId);

    List<Share> findByWithUserId(Long withUserId);

    List<Share> findByNodeIdAndWithUserId(Long nodeId, Long withUserId);

    long countByNodeId(Long nodeId);

    List<Share> findByOwnerId(Long ownerId);
}
