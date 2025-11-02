package com.devcool.adapters.out.persistence.user.repository;

import com.devcool.adapters.out.persistence.user.entity.UserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, Integer> {
  Optional<UserEntity> findByEmail(String email);

  boolean existsByEmail(String email);

  Optional<UserEntity> findByUsername(String username);

  boolean existsByUsername(String username);
}
