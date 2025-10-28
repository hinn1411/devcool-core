package com.devcool.application.service;

import com.devcool.domain.auth.out.PasswordHasherPort;
import com.devcool.domain.user.exception.EmailAlreadyUsedException;
import com.devcool.domain.user.exception.UserNotFoundException;
import com.devcool.domain.user.exception.UsernameAlreadyUsedException;
import com.devcool.domain.user.model.User;
import com.devcool.domain.user.model.enums.Role;
import com.devcool.domain.user.model.enums.UserStatus;
import com.devcool.domain.user.port.in.ChangePasswordUseCase;
import com.devcool.domain.user.port.in.GetUserQuery;
import com.devcool.domain.user.port.in.RegisterUserUseCase;
import com.devcool.domain.user.port.in.command.RegisterUserCommand;
import com.devcool.domain.user.port.out.UserPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService implements GetUserQuery, RegisterUserUseCase, ChangePasswordUseCase {

    private final UserPort userPort;
    private final PasswordHasherPort hasher;


    @Override
    @Transactional
    public boolean change(Integer userId, String currentRawPassword, String newRawPassword) {
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public User byId(Integer id) {
        return userPort.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> byEmail(String email) {
        return Optional.empty();
    }

    @Override
    @Transactional
    public Integer register(RegisterUserCommand command) {
        if (userPort.existsByUsername(command.username())) {
            throw new UsernameAlreadyUsedException(command.username());
        }

        if (userPort.existsByEmail(command.email())) {
            throw new EmailAlreadyUsedException(command.email());
        }

        User newUser = buildUser(command);
        return userPort.save(newUser);
    }

    private User buildUser(RegisterUserCommand command) {
        return User.builder()
                .username(command.username())
                .password(hasher.hash(command.rawPassword()))
                .email(command.email())
                .name(command.name())
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build();
    }
}
