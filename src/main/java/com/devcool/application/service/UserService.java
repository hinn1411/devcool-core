package com.devcool.application.service;

import com.devcool.adapter.out.persistence.UserEntity;
import com.devcool.domain.model.User;
import com.devcool.domain.model.enums.Role;
import com.devcool.domain.model.enums.UserStatus;
import com.devcool.domain.port.in.ChangePasswordUseCase;
import com.devcool.domain.port.in.GetUserQuery;
import com.devcool.domain.port.in.RegisterUserUseCase;
import com.devcool.domain.port.in.command.RegisterUserCommand;
import com.devcool.domain.port.out.UserPort;
import jakarta.transaction.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class UserService implements GetUserQuery, RegisterUserUseCase, ChangePasswordUseCase {

    private final UserPort repo;
    private final PasswordEncoder encoder;

    public UserService(UserPort repo, PasswordEncoder encoder) {
        this.repo = repo;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public boolean change(Integer userId, String currentRawPassword, String newRawPassword) {
        return false;
    }

    @Override
    @Transactional
    public Optional<UserEntity> byId(Integer id) {
        return Optional.empty();
    }

    @Override
    public Optional<UserEntity> byEmail(String email) {
        return Optional.empty();
    }

    @Override
    @Transactional
    public Integer register(RegisterUserCommand command) {
        User newUser = buildUser(command);
        repo.save(newUser);
        return 0;
    }

    private User buildUser(RegisterUserCommand command) {
        return User.builder()
                .username(command.username())
                .password(encoder.encode(command.rawPassword()))
                .email(command.email())
                .name(command.name())
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .lastLoginTime(Instant.now())
                .build();
    }
}
