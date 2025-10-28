package com.devcool.domain.auth.out;

public interface PasswordHasherPort {
    String hash(CharSequence rawPassword);
    boolean matches(CharSequence rawPassword, String encodedPassword);
}
