package com.pmis.docket.server.repo;

import com.pmis.docket.server.model.SessionToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface SessionRepository extends JpaRepository<SessionToken, String> {
    void deleteByExpiresAtBefore(Instant time);

    void deleteByUserId(Long userId);
}
