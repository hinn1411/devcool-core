package com.devcool.domain.user.port.in;

import com.devcool.domain.user.model.User;

import java.util.Optional;

public interface GetUserQuery {
    User byId(Integer id);
    Optional<User> byEmail(String email);
}
