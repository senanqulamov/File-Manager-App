package com.pmis.docket.server.repo;

import com.pmis.docket.server.model.Node;
import com.pmis.docket.server.model.NodeType;
import com.pmis.docket.server.model.Space;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NodeRepository extends JpaRepository<Node, Long> {
    List<Node> findByParentIdAndDeletedFalse(Long parentId);

    List<Node> findByParentId(Long parentId);

    long countByParentIdAndDeletedFalse(Long parentId);

    Optional<Node> findFirstByTypeAndSpaceAndOwnerId(NodeType type, Space space, Long ownerId);

    Optional<Node> findFirstByTypeAndSpace(NodeType type, Space space);

    List<Node> findByDeletedTrueAndDeleteRootTrueOrderByDeletedAtDesc();

    List<Node> findByDeletedTrueAndDeletedAtBefore(Instant time);

    long countByBlobKey(String blobKey);

    @Query("select coalesce(sum(n.sizeBytes), 0) from Node n where n.space = :space and n.ownerId = :ownerId and n.type = :type and n.deleted = false")
    long sumSize(@Param("space") Space space, @Param("ownerId") Long ownerId, @Param("type") NodeType type);

    @Query("select coalesce(sum(n.sizeBytes), 0) from Node n where n.type = :type and n.deleted = false")
    long sumAll(@Param("type") NodeType type);

    @Query("select coalesce(sum(n.sizeBytes), 0) from Node n where n.type = :type and n.deleted = true")
    long sumDeleted(@Param("type") NodeType type);

    long countByCheckedOutByIsNotNullAndDeletedFalse();
}
