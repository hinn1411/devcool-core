package com.devcool.adapter.in.web.controller;

import com.devcool.adapter.in.web.dto.RegisterUserRequest;
import com.devcool.domain.port.in.GetUserQuery;
import com.devcool.domain.port.in.RegisterUserUseCase;
import com.devcool.domain.port.in.command.RegisterUserCommand;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class UserController {
    private final RegisterUserUseCase registerUser;
    private final GetUserQuery getUser;

    public UserController(RegisterUserUseCase registerUser,
                          GetUserQuery getUser) {
        this.registerUser = registerUser;
        this.getUser = getUser;
    }

    @PostMapping("/register")
    public ResponseEntity<Integer> register(@Valid @RequestBody RegisterUserRequest request) {
        RegisterUserCommand newUser = createNewUser(request);

        Integer id = registerUser.register(newUser);

        return ResponseEntity.ok(id);
    }

    RegisterUserCommand createNewUser(RegisterUserRequest request) {
        return new RegisterUserCommand(
                request.username(), request.password(), request.email(), request.name(), request.role()
        );
    }
}
