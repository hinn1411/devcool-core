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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService implements GetUserQuery, RegisterUserUseCase, ChangePasswordUseCase {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private final UserPort userPort;
    private final PasswordHasherPort hasher;


    @Override
    @Transactional
    public boolean change(Integer id, String currentRawPassword, String newRawPassword) {
        User user = userPort.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        if (!hasher.matches(currentRawPassword, user.getPassword())) {
            log.info("Password does not match!");
            return false;
        }
        String newHashPassword = hasher.hash(newRawPassword);
        return userPort.updatePassword(id, newHashPassword);
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
