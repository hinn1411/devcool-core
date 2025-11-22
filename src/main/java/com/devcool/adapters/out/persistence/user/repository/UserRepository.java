package com.devcool.adapters.out.persistence.user.repository;

import com.devcool.adapters.out.persistence.user.entity.UserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserEntity, Integer> {
  Optional<UserEntity> findByEmail(String email);

  boolean existsByEmail(String email);

  Optional<UserEntity> findByUsername(String username);

  boolean existsByUsername(String username);

  @Modifying
  @Query(
      value =
          """
      UPDATE app_user
        SET token_version = token_version + 1
      WHERE id = :userId
      """,
      nativeQuery = true)
  int updateTokenVersion(@Param("userId") Integer userId);
}
