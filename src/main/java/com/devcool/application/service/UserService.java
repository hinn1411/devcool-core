package com.devcool.application.service;

import com.devcool.domain.auth.exception.PasswordDuplicateException;
import com.devcool.domain.auth.exception.PasswordIncorrectException;
import com.devcool.domain.auth.exception.PasswordNotMatchException;
import com.devcool.domain.auth.port.out.AccessTokenPort;
import com.devcool.domain.auth.port.out.PasswordHasherPort;
import com.devcool.domain.auth.port.out.RefreshTokenPort;
import com.devcool.domain.user.exception.EmailAlreadyUsedException;
import com.devcool.domain.user.exception.UserNotFoundException;
import com.devcool.domain.user.exception.UsernameAlreadyUsedException;
import com.devcool.domain.user.model.User;
import com.devcool.domain.user.model.enums.Role;
import com.devcool.domain.user.model.enums.UserStatus;
import com.devcool.domain.user.port.in.ChangePasswordUseCase;
import com.devcool.domain.user.port.in.GetUserQuery;
import com.devcool.domain.user.port.in.RegisterUserUseCase;
import com.devcool.domain.user.port.in.command.ChangePasswordCommand;
import com.devcool.domain.user.port.in.command.RegisterUserCommand;
import com.devcool.domain.user.port.out.UserPort;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService implements GetUserQuery, RegisterUserUseCase, ChangePasswordUseCase {

  private static final Logger log = LoggerFactory.getLogger(UserService.class);
  private final UserPort userPort;
  private final PasswordHasherPort passwordHasherPort;
  private final AccessTokenPort accessTokenPort;
  private final RefreshTokenPort refreshTokenPort;

  /**
   * Changes the password and logs the user out everywhere: every access token is invalidated by the
   * version bump and every refresh token is deleted, all in one transaction.
   */
  @Override
  @Transactional
  public void change(ChangePasswordCommand command) {
    String currentPassword = command.currentPassword();
    String newPassword = command.newPassword();
    String confirmedPassword = command.confirmedPassword();

    if (!newPassword.equals(confirmedPassword)) {
      log.warn("New password and its confirmation do not match");
      throw new PasswordNotMatchException();
    }

    if (newPassword.equals(currentPassword)) {
      log.warn("Current password and new password are the same");
      throw new PasswordDuplicateException();
    }

    User user =
        userPort
            .findById(command.userId())
            .orElseThrow(() -> new UserNotFoundException(command.userId()));

    if (!passwordHasherPort.matches(currentPassword, user.getPassword())) {
      log.warn("Current password does not match hashed password");
      throw new PasswordIncorrectException();
    }

    String hashedNewPassword = passwordHasherPort.hash(newPassword);
    // Fail closed: a write that changed no row must not look like success.
    if (!userPort.updatePassword(user.getId(), hashedNewPassword)) {
      throw new UserNotFoundException(command.userId());
    }
    accessTokenPort.updateVersion(user.getId());
    refreshTokenPort.deleteOldRefreshTokens(user.getId());
  }

  @Override
  @Transactional(readOnly = true)
  public User byId(Integer id) {
    return userPort.findById(id).orElseThrow(() -> new UserNotFoundException(id));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<User> byEmail(String email) {
    return userPort.findByEmail(normalizeEmail(email));
  }

  @Override
  @Transactional
  public Integer register(RegisterUserCommand command) {
    if (userPort.existsByUsername(command.username())) {
      throw new UsernameAlreadyUsedException(command.username());
    }

    String email = normalizeEmail(command.email());
    if (userPort.existsByEmail(email)) {
      throw new EmailAlreadyUsedException(email);
    }

    User newUser = buildUser(command, email);
    return userPort.save(newUser);
  }

  // Emails are case-insensitive: "Alice@x.com" and "alice@x.com" are the same account.
  private static String normalizeEmail(String email) {
    return email.toLowerCase(Locale.ROOT);
  }

  private User buildUser(RegisterUserCommand command, String email) {
    return User.builder()
        .username(command.username())
        .password(passwordHasherPort.hash(command.rawPassword()))
        .email(email)
        .name(command.name())
        .role(Role.USER)
        .status(UserStatus.ACTIVE)
        .tokenVersion(1)
        .build();
  }
}
