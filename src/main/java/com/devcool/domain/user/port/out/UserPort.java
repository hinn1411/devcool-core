package com.devcool.domain.user.port.out;

import com.devcool.domain.user.model.User;

import java.util.Optional;

public interface UserPort {
    Optional<User> findById(Integer id);
    boolean existsByEmail(String email);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    Integer save(User user);                 // create or update, return id
    boolean updatePassword(Integer id, String newHash);
    boolean remove(Integer id);
}
