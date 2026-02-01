package com.devcool.domain.auth.port.out;

public interface PasswordHasherPort {
  String hash(CharSequence rawPassword);

  boolean matches(CharSequence rawPassword, String encodedPassword);
}
