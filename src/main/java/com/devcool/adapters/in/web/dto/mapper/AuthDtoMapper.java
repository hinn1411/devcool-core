package com.devcool.adapters.in.web.dto.mapper;

import com.devcool.adapters.in.web.dto.request.RegisterUserRequest;
import com.devcool.adapters.in.web.dto.response.GetProfileResponse;
import com.devcool.adapters.in.web.dto.response.LoginResponse;
import com.devcool.adapters.in.web.dto.response.RefreshTokenResponse;
import com.devcool.adapters.in.web.dto.response.RegisterUserResponse;
import com.devcool.domain.auth.model.TokenPair;
import com.devcool.domain.user.model.User;
import com.devcool.domain.user.port.in.command.RegisterUserCommand;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AuthDtoMapper {

  @Mapping(target = "userId", source = "userId")
  RegisterUserResponse toRegisterResponse(Integer userId);

  LoginResponse toLoginResponse(TokenPair tokenPair);

  RefreshTokenResponse toRefreshTokenResponse(TokenPair tokenPair);

  GetProfileResponse toProfileResponse(User user);

  @Mapping(target = "rawPassword", source = "password")
  RegisterUserCommand toRegisterCommand(RegisterUserRequest request);
}
