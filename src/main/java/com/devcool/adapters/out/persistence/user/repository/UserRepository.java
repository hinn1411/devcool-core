package com.devcool.adapters.out.persistence.user.repository;

import com.devcool.adapters.out.persistence.user.entity.UserEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

  List<UserEntity> findByIdIn(Collection<Integer> ids);

  @Query(value = "SELECT id FROM app_user WHERE id in :userIds", nativeQuery = true)
  Set<Integer> findExistingIds(@Param("userIds") Collection<Integer> userIds);
}
