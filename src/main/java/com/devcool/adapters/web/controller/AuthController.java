package com.devcool.adapters.web.controller;

import com.devcool.adapters.web.dto.mapper.AuthDtoMapper;
import com.devcool.adapters.web.dto.request.ChangePasswordRequest;
import com.devcool.adapters.web.dto.request.LoginRequest;
import com.devcool.adapters.web.dto.request.RegisterUserRequest;
import com.devcool.adapters.web.dto.response.GetProfileResponse;
import com.devcool.adapters.web.dto.response.LoginResponse;
import com.devcool.adapters.web.dto.response.RegisterUserResponse;
import com.devcool.adapters.web.dto.schema.GetProfileSuccess;
import com.devcool.adapters.web.dto.schema.LoginSuccess;
import com.devcool.adapters.web.dto.schema.RegisterUserSuccess;
import com.devcool.adapters.web.dto.wrapper.ApiErrorResponse;
import com.devcool.adapters.web.dto.wrapper.ApiSuccessResponse;
import com.devcool.adapters.web.util.ApiResponseFactory;
import com.devcool.domain.auth.in.AuthenticateUserUseCase;
import com.devcool.domain.auth.in.command.LoginCommand;
import com.devcool.domain.auth.model.TokenPair;
import com.devcool.domain.common.ErrorCode;
import com.devcool.domain.user.model.User;
import com.devcool.domain.user.port.in.ChangePasswordUseCase;
import com.devcool.domain.user.port.in.GetUserQuery;
import com.devcool.domain.user.port.in.RegisterUserUseCase;
import com.devcool.domain.user.port.in.command.RegisterUserCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
  private static final Logger log = LoggerFactory.getLogger(AuthController.class);
  private final RegisterUserUseCase registerUser;
  private final AuthenticateUserUseCase authenticate;
  private final ChangePasswordUseCase changePassword;
  private final GetUserQuery userQuery;
  private final AuthDtoMapper mapper;

  @Operation(
      summary = "Register account",
      description = "Register a new account",
      requestBody =
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
              required = true,
              content = @Content(schema = @Schema(implementation = RegisterUserRequest.class))),
      responses = {
        @ApiResponse(
            responseCode = "201",
            description = "Created",
            content = @Content(schema = @Schema(implementation = RegisterUserSuccess.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Bad Request",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(
            responseCode = "409",
            description = "Conflict (username/email used)",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(
            responseCode = "500",
            description = "Server error",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
      })
  @PostMapping("/register")
  public ResponseEntity<ApiSuccessResponse<RegisterUserResponse>> register(
      @Valid @RequestBody RegisterUserRequest request) {
    RegisterUserCommand newUser = mapper.toRegisterCommand(request);

    Integer id = registerUser.register(newUser);
    RegisterUserResponse response = mapper.toRegisterResponse(id);
    return ResponseEntity.created(URI.create("/api/v1/auth/" + id))
        .body(
            ApiResponseFactory.success(
                HttpStatus.CREATED,
                ErrorCode.CREATED.code(),
                "User register successfully",
                response));
  }

  @Operation(
      summary = "Login",
      description = "Authenticate user and return login result",
      requestBody =
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
              required = true,
              content = @Content(schema = @Schema(implementation = LoginRequest.class))),
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(schema = @Schema(implementation = LoginSuccess.class))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
      })
  @PostMapping("/login")
  public ResponseEntity<ApiSuccessResponse<LoginResponse>> login(
      @Valid @RequestBody LoginRequest request) {
    LoginCommand command = new LoginCommand(request.username(), request.password());
    LoginCommand nullCommand1 = null;
    nullCommand1.password();
    TokenPair tokens = authenticate.login(command);
    LoginResponse response = mapper.toLoginResponse(tokens);
    return ResponseEntity.ok(
        ApiResponseFactory.success(
            HttpStatus.OK, ErrorCode.OK.code(), "Login successfully", response));
  }

  @PostMapping("/password")
  public ResponseEntity<ApiSuccessResponse<Boolean>> changePassword(
      @Valid @RequestBody ChangePasswordRequest request) {
    return null;
  }

  @Operation(
      summary = "Get my profile",
      security = {@SecurityRequirement(name = "bearerAuth")},
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(schema = @Schema(implementation = GetProfileSuccess.class))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
      })
  @GetMapping("/profile")
  public ResponseEntity<ApiSuccessResponse<GetProfileResponse>> getProfile(Authentication auth) {
    Integer userId = Integer.valueOf(auth.getName());
    User user = userQuery.byId(userId);
    GetProfileResponse response = mapper.toProfileResponse(user);
    return ResponseEntity.ok(
        ApiResponseFactory.success(
            HttpStatus.OK, ErrorCode.OK.code(), "Get user profile successfully", response));
  }
}
