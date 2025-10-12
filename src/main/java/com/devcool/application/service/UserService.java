package com.devcool.application.service;

import com.devcool.adapter.out.persistence.UserEntity;
import com.devcool.domain.port.in.ChangePasswordUseCase;
import com.devcool.domain.port.in.GetUserQuery;
import com.devcool.domain.port.in.RegisterUserUseCase;
import com.devcool.domain.port.in.command.RegisterUserCommand;
import com.devcool.domain.port.out.UserRepositoryPort;
import jakarta.transaction.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService implements GetUserQuery, RegisterUserUseCase, ChangePasswordUseCase {

    private final UserRepositoryPort repo;
    private final PasswordEncoder encoder;

    public UserService(UserRepositoryPort repo, PasswordEncoder encoder) {
        this.repo = repo;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public boolean change(Integer userId, String currentRawPassword, String newRawPassword) {
        return false;
    }

    @Override
    public Optional<UserEntity> byId(Integer id) {
        return Optional.empty();
    }

    @Override
    public Optional<UserEntity> byEmail(String email) {
        return Optional.empty();
    }

    @Override
    @Transactional
    public Integer register(RegisterUserCommand cmd) {
        return 0;
    }
}
