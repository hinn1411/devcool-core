package com.devcool.domain.port.in;

import com.devcool.adapter.out.persistence.UserEntity;

import java.util.Optional;

public interface GetUserQuery {
    Optional<UserEntity> byId(Integer id);
    Optional<UserEntity> byEmail(String email);
}
