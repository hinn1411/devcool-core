package com.devcool.adapters.out.crypto;

import com.devcool.domain.auth.out.PasswordHasherPort;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class PasswordHasherAdapter implements PasswordHasherPort {
    private final PasswordEncoder encoder;

    @Override
    public String hash(CharSequence rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence raw, String encoded) {
        return encoder.matches(raw, encoded);
    }
}
