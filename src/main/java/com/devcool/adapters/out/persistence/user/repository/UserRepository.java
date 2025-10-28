package com.devcool.adapters.out.persistence.user.repository;

import com.devcool.adapters.out.persistence.user.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Integer> {
    Optional<UserEntity> findByEmail(String email);
    boolean existsByEmail(String email);

    Optional<UserEntity> findByUsername(String username);
    boolean existsByUsername(String username);
}
