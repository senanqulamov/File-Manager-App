package com.pmis.docket.server.repo;

import com.pmis.docket.server.model.FileVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FileVersionRepository extends JpaRepository<FileVersion, Long> {
    List<FileVersion> findByNodeIdOrderByNumberDesc(Long nodeId);

    Optional<FileVersion> findByNodeIdAndNumber(Long nodeId, int number);

    long countByBlobKey(String blobKey);

    List<FileVersion> findByNodeId(Long nodeId);
}
