package com.pmis.docket.server.repo;

import com.pmis.docket.server.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByLoginIgnoreCase(String login);
}
