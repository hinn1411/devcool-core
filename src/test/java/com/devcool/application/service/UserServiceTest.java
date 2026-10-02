package com.devcool.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import com.devcool.domain.user.port.in.command.ChangePasswordCommand;
import com.devcool.domain.user.port.in.command.RegisterUserCommand;
import com.devcool.domain.user.port.out.UserPort;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  private static final int USER_ID = 7;
  private static final int SAVED_ID = 42;
  private static final String USERNAME = "alice";
  private static final String EMAIL = "alice@devcool.com";
  private static final String NAME = "Alice";
  private static final String RAW_PASSWORD = "secret";
  private static final String HASHED_PASSWORD = "HASHED(secret)";
  private static final String STORED_HASH = "HASHED(old-secret)";
  private static final String OLD_RAW_PASSWORD = "old-secret";
  private static final String NEW_RAW_PASSWORD = "new-secret";
  private static final String NEW_HASHED_PASSWORD = "HASHED(new-secret)";

  @Mock private UserPort userPort;
  @Mock private PasswordHasherPort passwordHasherPort;
  @Mock private AccessTokenPort accessTokenPort;
  @Mock private RefreshTokenPort refreshTokenPort;

  @InjectMocks private UserService userService;

  @Nested
  class Register {

    @Test
    void usernameTaken_throwsUsernameAlreadyUsed_andNothingIsHashedOrSaved() {
      when(userPort.existsByUsername(USERNAME)).thenReturn(true);

      assertThatThrownBy(() -> userService.register(registerCommand()))
          .isInstanceOfSatisfying(
              UsernameAlreadyUsedException.class,
              ex ->
                  assertThat(ex.getDetails())
                      .containsEntry("username", USERNAME)
                      .doesNotContainValue(RAW_PASSWORD));
      assertNothingHashedOrSaved();
    }

    @Test
    void emailTaken_throwsEmailAlreadyUsed_andNothingIsHashedOrSaved() {
      when(userPort.existsByUsername(USERNAME)).thenReturn(false);
      when(userPort.existsByEmail(EMAIL)).thenReturn(true);

      assertThatThrownBy(() -> userService.register(registerCommand()))
          .isInstanceOfSatisfying(
              EmailAlreadyUsedException.class,
              ex ->
                  assertThat(ex.getDetails())
                      .containsEntry("email", EMAIL)
                      .doesNotContainValue(RAW_PASSWORD));
      assertNothingHashedOrSaved();
    }

    @Test
    void usernameAndEmailTaken_throwsUsernameAlreadyUsed_withoutCheckingEmail() {
      when(userPort.existsByUsername(USERNAME)).thenReturn(true);
      // existsByEmail is not stubbed: the service must not reach it.

      assertThatThrownBy(() -> userService.register(registerCommand()))
          .isInstanceOf(UsernameAlreadyUsedException.class);
      verify(userPort, never()).existsByEmail(any());
      assertNothingHashedOrSaved();
    }

    @Test
    void newUser_savesHashedActiveUser_andReturnsSavedId() {
      when(userPort.existsByUsername(USERNAME)).thenReturn(false);
      when(userPort.existsByEmail(EMAIL)).thenReturn(false);
      when(passwordHasherPort.hash(RAW_PASSWORD)).thenReturn(HASHED_PASSWORD);
      when(userPort.save(any(User.class))).thenReturn(SAVED_ID);

      Integer id = userService.register(registerCommand());

      assertThat(id).isEqualTo(SAVED_ID);
      // id, emailVerified, avatar and lastLoginTime must stay null.
      User expected = activeUser().password(HASHED_PASSWORD).build();
      assertThat(capturedSavedUser()).usingRecursiveComparison().isEqualTo(expected);
    }

    // Emails are case-insensitive: lowercased before the duplicate check and before saving.
    @Test
    void mixedCaseEmail_isLowercasedForDuplicateCheckAndSave() {
      when(userPort.existsByUsername(USERNAME)).thenReturn(false);
      when(passwordHasherPort.hash(RAW_PASSWORD)).thenReturn(HASHED_PASSWORD);
      when(userPort.save(any(User.class))).thenReturn(SAVED_ID);

      userService.register(
          new RegisterUserCommand(USERNAME, RAW_PASSWORD, "Alice@DevCool.com", NAME));

      assertThat(capturedSavedUser().getEmail()).isEqualTo(EMAIL);
      verify(userPort).existsByEmail(EMAIL);
    }

    private void assertNothingHashedOrSaved() {
      verifyNoInteractions(passwordHasherPort);
      verify(userPort, never()).save(any());
    }

    private User capturedSavedUser() {
      ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
      verify(userPort).save(captor.capture());
      return captor.getValue();
    }
  }

  @Nested
  class Change {

    private ChangePasswordCommand command() {
      return new ChangePasswordCommand(
          USER_ID, OLD_RAW_PASSWORD, NEW_RAW_PASSWORD, NEW_RAW_PASSWORD);
    }

    private void assertNothingStoredOrRevoked() {
      verify(passwordHasherPort, never()).hash(any());
      verify(userPort, never()).updatePassword(any(), any());
      verifyNoInteractions(accessTokenPort, refreshTokenPort);
    }

    @Test
    void correctCurrentPassword_storesTheNewHashAndLogsTheUserOutEverywhere() {
      when(userPort.findById(USER_ID)).thenReturn(Optional.of(storedUser()));
      when(passwordHasherPort.matches(OLD_RAW_PASSWORD, STORED_HASH)).thenReturn(true);
      when(passwordHasherPort.hash(NEW_RAW_PASSWORD)).thenReturn(NEW_HASHED_PASSWORD);
      when(userPort.updatePassword(USER_ID, NEW_HASHED_PASSWORD)).thenReturn(true);

      userService.change(command());

      verify(userPort).updatePassword(USER_ID, NEW_HASHED_PASSWORD);
      verify(accessTokenPort).updateVersion(USER_ID);
      verify(refreshTokenPort).deleteOldRefreshTokens(USER_ID);
    }

    @Test
    void confirmationDiffersFromNewPassword_throwsPasswordNotMatch_beforeTouchingAnyPort() {
      ChangePasswordCommand mismatched =
          new ChangePasswordCommand(USER_ID, OLD_RAW_PASSWORD, NEW_RAW_PASSWORD, "something-else");

      assertThatThrownBy(() -> userService.change(mismatched))
          .isInstanceOf(PasswordNotMatchException.class);

      verifyNoInteractions(userPort, passwordHasherPort, accessTokenPort, refreshTokenPort);
    }

    @Test
    void unknownUser_throwsUserNotFound_andNothingIsStoredOrRevoked() {
      when(userPort.findById(USER_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> userService.change(command()))
          .isInstanceOfSatisfying(
              UserNotFoundException.class,
              ex -> assertThat(ex.getDetails()).containsEntry("userId", USER_ID));

      verifyNoInteractions(passwordHasherPort);
      assertNothingStoredOrRevoked();
    }

    @Test
    void wrongCurrentPassword_throwsPasswordIncorrect_andNothingIsStoredOrRevoked() {
      when(userPort.findById(USER_ID)).thenReturn(Optional.of(storedUser()));
      when(passwordHasherPort.matches(OLD_RAW_PASSWORD, STORED_HASH)).thenReturn(false);

      assertThatThrownBy(() -> userService.change(command()))
          .isInstanceOfSatisfying(
              PasswordIncorrectException.class, ex -> assertThat(ex.getDetails()).isEmpty());

      assertNothingStoredOrRevoked();
    }

    @Test
    void newPasswordSameAsCurrent_throwsPasswordDuplicate_beforeTouchingAnyPort() {
      ChangePasswordCommand unchanged =
          new ChangePasswordCommand(USER_ID, OLD_RAW_PASSWORD, OLD_RAW_PASSWORD, OLD_RAW_PASSWORD);

      assertThatThrownBy(() -> userService.change(unchanged))
          .isInstanceOf(PasswordDuplicateException.class);

      verifyNoInteractions(userPort, passwordHasherPort, accessTokenPort, refreshTokenPort);
    }

    // Fail closed: a write that changed no row must not look like success.
    @Test
    void passwordUpdateAffectsNoRow_throwsUserNotFound_andNothingIsRevoked() {
      when(userPort.findById(USER_ID)).thenReturn(Optional.of(storedUser()));
      when(passwordHasherPort.matches(OLD_RAW_PASSWORD, STORED_HASH)).thenReturn(true);
      when(passwordHasherPort.hash(NEW_RAW_PASSWORD)).thenReturn(NEW_HASHED_PASSWORD);
      when(userPort.updatePassword(USER_ID, NEW_HASHED_PASSWORD)).thenReturn(false);

      assertThatThrownBy(() -> userService.change(command()))
          .isInstanceOf(UserNotFoundException.class);

      verifyNoInteractions(accessTokenPort, refreshTokenPort);
    }
  }

  @Nested
  class ById {

    @Test
    void existingUser_returnsIt() {
      User user = storedUser();
      when(userPort.findById(USER_ID)).thenReturn(Optional.of(user));

      assertThat(userService.byId(USER_ID)).isSameAs(user);
    }

    @Test
    void unknownUser_throwsUserNotFound() {
      when(userPort.findById(USER_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> userService.byId(USER_ID))
          .isInstanceOfSatisfying(
              UserNotFoundException.class,
              ex -> assertThat(ex.getDetails()).containsEntry("userId", USER_ID));
    }
  }

  @Nested
  class ByEmail {

    @Test
    void unknownEmail_returnsEmpty() {
      when(userPort.findByEmail(EMAIL)).thenReturn(Optional.empty());

      assertThat(userService.byEmail(EMAIL)).isEmpty();
    }

    @Test
    void existingEmail_returnsUser() {
      User user = storedUser();
      when(userPort.findByEmail(EMAIL)).thenReturn(Optional.of(user));

      assertThat(userService.byEmail(EMAIL)).containsSame(user);
    }
  }

  private static RegisterUserCommand registerCommand() {
    return new RegisterUserCommand(USERNAME, RAW_PASSWORD, EMAIL, NAME);
  }

  private static User storedUser() {
    return activeUser().id(USER_ID).password(STORED_HASH).build();
  }

  // A freshly registered user; callers add id and password.
  private static User.UserBuilder activeUser() {
    return User.builder()
        .username(USERNAME)
        .email(EMAIL)
        .name(NAME)
        .role(Role.USER)
        .status(UserStatus.ACTIVE)
        .tokenVersion(1);
  }
}
