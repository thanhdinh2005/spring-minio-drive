package com.spring.backend.repository;

import com.spring.backend.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
  @EntityGraph(attributePaths = {"roles", "roles.permissions"})
  Optional<User> findByEmail(String email);
}
