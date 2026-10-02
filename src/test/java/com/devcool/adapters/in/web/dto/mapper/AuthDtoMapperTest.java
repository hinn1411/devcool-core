package com.devcool.adapters.in.web.dto.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.devcool.adapters.in.web.dto.request.ChangePasswordRequest;
import com.devcool.adapters.in.web.dto.request.RegisterUserRequest;
import com.devcool.adapters.in.web.dto.response.GetProfileResponse;
import com.devcool.adapters.in.web.dto.response.LoginResponse;
import com.devcool.adapters.in.web.dto.response.RefreshTokenResponse;
import com.devcool.adapters.in.web.dto.response.RegisterUserResponse;
import com.devcool.domain.auth.model.TokenPair;
import com.devcool.domain.user.model.User;
import com.devcool.domain.user.model.enums.Role;
import com.devcool.domain.user.model.enums.UserStatus;
import com.devcool.domain.user.port.in.command.ChangePasswordCommand;
import com.devcool.domain.user.port.in.command.RegisterUserCommand;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AuthDtoMapperTest {

  private final AuthDtoMapper mapper = new AuthDtoMapperImpl();

  @Test
  void toRegisterResponse_carriesUserId() {
    RegisterUserResponse response = mapper.toRegisterResponse(42);

    assertThat(response.getUserId()).isEqualTo(42);
  }

  @Test
  void toLoginResponse_carriesOnlyTheAccessToken() {
    LoginResponse response = mapper.toLoginResponse(new TokenPair("access-token", "refresh-token"));

    assertThat(response.getAccessToken()).isEqualTo("access-token");
  }

  @Test
  void toRefreshTokenResponse_carriesOnlyTheAccessToken() {
    RefreshTokenResponse response =
        mapper.toRefreshTokenResponse(new TokenPair("access-token", "refresh-token"));

    assertThat(response.getAccessToken()).isEqualTo("access-token");
  }

  @Test
  void toRegisterCommand_mapsPasswordToRawPassword() {
    RegisterUserRequest request =
        new RegisterUserRequest("hien_giang", "s3cret-pass", "hien@example.com", "Hien Giang");

    RegisterUserCommand command = mapper.toRegisterCommand(request);

    assertThat(command.username()).isEqualTo("hien_giang");
    assertThat(command.rawPassword()).isEqualTo("s3cret-pass");
    assertThat(command.email()).isEqualTo("hien@example.com");
    assertThat(command.name()).isEqualTo("Hien Giang");
  }

  @Test
  void toChangePasswordCommand_carriesTheCallerIdAndEveryPassword() {
    ChangePasswordRequest request =
        new ChangePasswordRequest("old-secret", "new-secret", "confirm-secret");

    ChangePasswordCommand command = mapper.toChangePasswordCommand(request, 7);

    assertThat(command.userId()).isEqualTo(7);
    assertThat(command.currentPassword()).isEqualTo("old-secret");
    assertThat(command.newPassword()).isEqualTo("new-secret");
    assertThat(command.confirmedPassword()).isEqualTo("confirm-secret");
  }

  @Test
  void toProfileResponse_mapsRoleAndStatusToTheirNames() {
    Instant lastLogin = Instant.parse("2025-10-12T15:30:00Z");
    User user =
        User.builder()
            .id(101)
            .username("hien_giang")
            .email("hien@example.com")
            .name("Hien Giang")
            .avatar("https://cdn.devcool.com/avatars/hien.png")
            .role(Role.USER)
            .status(UserStatus.ACTIVE)
            .lastLoginTime(lastLogin)
            .emailVerified(true)
            .build();

    GetProfileResponse response = mapper.toProfileResponse(user);

    assertThat(response.getId()).isEqualTo("101");
    assertThat(response.getRole()).isEqualTo("USER");
    assertThat(response.getStatus()).isEqualTo("ACTIVE");
    assertThat(response.getUsername()).isEqualTo("hien_giang");
    assertThat(response.getEmail()).isEqualTo("hien@example.com");
    assertThat(response.getName()).isEqualTo("Hien Giang");
    assertThat(response.getAvatar()).isEqualTo("https://cdn.devcool.com/avatars/hien.png");
    assertThat(response.getLastLoginTime()).isEqualTo(lastLogin);
    assertThat(response.getEmailVerified()).isTrue();
  }

  // Intentional change from the hand-written mapper, which rendered the literal string "null".
  @Test
  void toProfileResponse_userWithoutId_leavesIdNull() {
    User user = User.builder().role(Role.USER).status(UserStatus.ACTIVE).build();

    assertThat(mapper.toProfileResponse(user).getId()).isNull();
  }

  @ParameterizedTest
  @EnumSource(Role.class)
  void toProfileResponse_everyRole_mapsToItsName(Role role) {
    User user = User.builder().id(1).role(role).status(UserStatus.ACTIVE).build();

    assertThat(mapper.toProfileResponse(user).getRole()).isEqualTo(role.name());
  }

  @ParameterizedTest
  @EnumSource(UserStatus.class)
  void toProfileResponse_everyStatus_mapsToItsName(UserStatus status) {
    User user = User.builder().id(1).role(Role.USER).status(status).build();

    assertThat(mapper.toProfileResponse(user).getStatus()).isEqualTo(status.name());
  }
}
