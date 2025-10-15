package com.devcool.domain.model;

import com.devcool.domain.model.enums.Role;
import com.devcool.domain.model.enums.UserStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Objects;

@Getter
@Setter
@Builder
public class User {
    private Integer id;
    private String username;
    private String password; // store hashed password
    private String email;
    private boolean isEmailVerified;
    private String name;
    private String avatar;
    private Role role;
    private UserStatus status;
    private Instant lastLoginTime;

    // constructors, getters, behavior methods…
    // Consider invariants & methods like changePassword(), verifyEmail(), deactivate(), etc.

    public void changePassword(String newPassword) {
        this.password = Objects.requireNonNull(newPassword);
    }

    // getters/setters omitted for brevity
}