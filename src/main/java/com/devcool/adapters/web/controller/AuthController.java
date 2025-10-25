package com.devcool.adapters.web.controller;

import com.devcool.adapters.web.dto.request.RegisterUserRequest;
import com.devcool.adapters.web.dto.wrapper.ApiSuccessResponse;
import com.devcool.adapters.web.util.ApiResponseFactory;
import com.devcool.domain.user.port.in.RegisterUserUseCase;
import com.devcool.domain.user.port.in.command.RegisterUserCommand;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final RegisterUserUseCase registerUser;


    public AuthController(RegisterUserUseCase registerUser) {
        this.registerUser = registerUser;
    }

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

    RegisterUserCommand createNewUser(RegisterUserRequest request) {
        return new RegisterUserCommand(request.username(), request.password(), request.email(), request.name()
        );
    }
}
