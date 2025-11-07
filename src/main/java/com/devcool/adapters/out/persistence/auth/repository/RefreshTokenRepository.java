package com.devcool.adapters.out.persistence.auth.repository;

import com.devcool.adapters.out.persistence.auth.entity.RefreshTokenEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, String> {
  Optional<RefreshTokenEntity> findByJti(String jti);
}
