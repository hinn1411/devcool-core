package com.devcool.adapters.web.controller;

import com.devcool.adapters.web.dto.request.LoginRequest;
import com.devcool.adapters.web.dto.request.RegisterUserRequest;
import com.devcool.adapters.web.dto.wrapper.ApiSuccessResponse;
import com.devcool.adapters.web.util.ApiResponseFactory;
import com.devcool.domain.auth.in.AuthenticateUserUseCase;
import com.devcool.domain.auth.in.command.LoginCommand;
import com.devcool.domain.auth.model.TokenPair;
import com.devcool.domain.common.ErrorCode;
import com.devcool.domain.user.port.in.RegisterUserUseCase;
import com.devcool.domain.user.port.in.command.RegisterUserCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final RegisterUserUseCase registerUser;
    private final AuthenticateUserUseCase authenticate;


    @PostMapping("/register")
    public ResponseEntity<ApiSuccessResponse<Integer>> register(@Valid @RequestBody RegisterUserRequest request) {
        RegisterUserCommand newUser = createNewUser(request);

        Integer id = registerUser.register(newUser);

        return ResponseEntity
                .created(URI.create("/api/v1/auth/" + id))
                .body(ApiResponseFactory.success(
                        HttpStatus.CREATED,
                        "USR_201",
                        "User register successfully",
                        id
                ));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiSuccessResponse<TokenPair>> login(@Valid @RequestBody LoginRequest request) {
        LoginCommand command = new LoginCommand(request.username(), request.password());
        TokenPair tokens = authenticate.login(command);
        return ResponseEntity
                .ok(ApiResponseFactory
                        .success(HttpStatus.OK,
                                ErrorCode.OK.code(),
                                "Login successfully",
                                tokens));
    }

    RegisterUserCommand createNewUser(RegisterUserRequest request) {
        return new RegisterUserCommand(request.username(), request.password(), request.email(), request.name()
        );
    }
}
