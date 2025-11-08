package com.devcool.adapters.out.persistence.auth.repository;

import com.devcool.adapters.out.persistence.auth.entity.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, String> {

  @Transactional
  @Modifying
  @Query(
      value =
          """
      UPDATE refresh_token
         SET consumed_time = now()
       WHERE jti = :jtiHash
         AND consumed_time IS NULL
         AND expired_time > now()
      """,
      nativeQuery = true)
  int consumeIfValid(@Param("jtiHash") String jtiHash);

  void deleteAllByUserId(String userId);
}
