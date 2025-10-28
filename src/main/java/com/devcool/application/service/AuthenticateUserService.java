package com.devcool.application.service;

import com.devcool.adapters.out.crypto.util.HashUtils;
import com.devcool.adapters.out.jwt.util.JwtUtils;
import com.devcool.domain.auth.in.AuthenticateUserUseCase;
import com.devcool.domain.auth.in.command.LoginCommand;
import com.devcool.domain.auth.model.RefreshToken;
import com.devcool.domain.auth.model.TokenPair;
import com.devcool.domain.auth.out.LoadUserPort;
import com.devcool.domain.auth.out.PasswordHasherPort;
import com.devcool.domain.auth.out.RefreshTokenStorePort;
import com.devcool.domain.auth.out.TokenIssuerPort;
import com.devcool.domain.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class AuthenticateUserService implements AuthenticateUserUseCase {
    private final LoadUserPort loadUser;
    private final PasswordHasherPort hasher;
    private final TokenIssuerPort issuer;
    private final RefreshTokenStorePort refreshStore;

    @Override
    @Transactional
    public TokenPair login(LoginCommand command) {
        User user = loadUser.loadByUsername(command.username())
                .orElseThrow(() -> new BadCredentialsException("Bad credentials"));

        if (!hasher.matches(command.password(), user.getPassword())) {
            throw new BadCredentialsException("Bad credentials");
        }

        TokenPair tokenPair = issuer.issue(user);
        RefreshToken refreshToken = buildRefreshToken(user, tokenPair);
        refreshStore.store(refreshToken);
        return tokenPair;

    }

    private String hashJtiFrom(String refreshToken) {
        return HashUtils.sha256(JwtUtils.jtiFrom(refreshToken));
    }

    private RefreshToken buildRefreshToken(User user, TokenPair tokenPair) {
        return RefreshToken.builder()
                .jti(hashJtiFrom(tokenPair.refreshToken()))
                .userId(user.getId())
                .issuedTime(Instant.now())
                .expiredTime(Instant.now().plus(7, ChronoUnit.DAYS))
                .consumedTime(null)
                .build();
    }
}
