package com.devcool.domain.port.out;

import com.devcool.adapter.out.persistence.UserEntity;

import java.util.Optional;

public interface UserRepositoryPort {
    Optional<UserEntity> findById(Integer id);
    Optional<UserEntity> findByEmail(String email);
    Integer save(UserEntity user);                 // create or update, return id
    boolean updatePassword(Integer id, String newHash);
    boolean remove(Integer id);
}
