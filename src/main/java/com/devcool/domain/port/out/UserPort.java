package com.devcool.domain.port.out;

import com.devcool.domain.model.User;

import java.util.Optional;

public interface UserPort {
    Optional<User> findById(Integer id);
    Optional<User> findByEmail(String email);
    Integer save(User user);                 // create or update, return id
    boolean updatePassword(Integer id, String newHash);
    boolean remove(Integer id);
}
