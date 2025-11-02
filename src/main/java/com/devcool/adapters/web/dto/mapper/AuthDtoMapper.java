package com.devcool.adapters.web.dto.mapper;

import com.devcool.adapters.web.dto.request.RegisterUserRequest;
import com.devcool.adapters.web.dto.response.GetProfileResponse;
import com.devcool.adapters.web.dto.response.LoginResponse;
import com.devcool.adapters.web.dto.response.RegisterUserResponse;
import com.devcool.domain.auth.model.TokenPair;
import com.devcool.domain.user.model.User;
import com.devcool.domain.user.port.in.command.RegisterUserCommand;
import org.springframework.stereotype.Component;

@Component
public class AuthDtoMapper {

  public RegisterUserResponse toRegisterResponse(Integer userId) {
    return RegisterUserResponse.builder().userId(userId).build();
  }

  public LoginResponse toLoginResponse(TokenPair tokenPair) {
    return LoginResponse.builder()
        .accessToken(tokenPair.accessToken())
        .refreshToken(tokenPair.refreshToken())
        .build();
  }

  public GetProfileResponse toProfileResponse(User user) {
    return GetProfileResponse.builder()
        .username(user.getUsername())
        .email(user.getEmail())
        .name(user.getName())
        .avatar(user.getAvatar())
        .role(user.getRole())
        .status(user.getStatus())
        .lastLoginTime(user.getLastLoginTime())
        .emailVerified(user.isEmailVerified())
        .build();
  }

  public RegisterUserCommand toRegisterCommand(RegisterUserRequest request) {
    return new RegisterUserCommand(
        request.username(), request.password(), request.email(), request.name());
  }
}
