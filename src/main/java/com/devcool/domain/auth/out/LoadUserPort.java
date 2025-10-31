package com.devcool.domain.auth.out;

import com.devcool.domain.user.model.User;

import java.util.Optional;

public interface LoadUserPort {
    Optional<User> loadByUsername(String username);
    Optional<User> loadById(Integer id);
}
