package com.devcool.domain.user.model;

import com.devcool.domain.user.model.enums.Role;
import com.devcool.domain.user.model.enums.UserStatus;
import java.time.Instant;
import java.util.Objects;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class User {
  private Integer id;
  private String username;
  private String password; // store hashed password
  private String email;
  private boolean emailVerified;
  private String name;
  private String avatar;
  private Role role;
  private UserStatus status;
  private Instant lastLoginTime;
  private Integer tokenVersion;

  // constructors, getters, behavior methods…
  // Consider invariants & methods like changePassword(), verifyEmail(), deactivate(), etc.

  public void changePassword(String newPassword) {
    this.password = Objects.requireNonNull(newPassword);
  }

  public void updateLoginTime() {
    this.lastLoginTime = Instant.now();
  }
  // getters/setters omitted for brevity
}
