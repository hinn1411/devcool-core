package com.devcool.domain.model;

import com.devcool.domain.model.enums.Role;
import com.devcool.domain.model.enums.UserStatus;

import java.time.Instant;
import java.util.Objects;

public class User {
    private Integer id;
    private String username;
    private String passwordHash; // store hashed password
    private String email;
    private boolean emailVerified;
    private String name;
    private String avatar;
    private Role role;
    private UserStatus status;
    private Instant lastLoginTime;

    // constructors, getters, behavior methods…
    // Consider invariants & methods like changePassword(), verifyEmail(), deactivate(), etc.

    public void changePassword(String newHash) {
        this.passwordHash = Objects.requireNonNull(newHash);
    }

    // getters/setters omitted for brevity
}