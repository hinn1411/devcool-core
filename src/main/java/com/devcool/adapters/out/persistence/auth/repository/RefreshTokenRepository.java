package com.devcool.adapters.out.persistence.auth.repository;

import com.devcool.adapters.out.persistence.auth.entity.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, String> {
    Optional<RefreshTokenEntity> findByJti(String jti);
}
