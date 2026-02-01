package com.devcool.adapters.in.web.security;

import com.devcool.adapters.out.jwt.util.JwtUtils;
import com.devcool.domain.auth.port.out.LoadUserPort;
import com.devcool.domain.auth.port.out.TokenIssuerPort;
import com.devcool.domain.user.model.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
  private final TokenIssuerPort issuer;
  private final LoadUserPort loader;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String header = request.getHeader("Authorization");
    if (Objects.isNull(header) || !header.startsWith("Bearer ")) {
      logger.warn("Token format is invalid!");
      SecurityContextHolder.clearContext();
      filterChain.doFilter(request, response);
      return;
    }

    String token = header.substring(7).trim();
    if (!issuer.isAccessTokenValid(token)) {
      logger.warn("Access token is invalid!");
      SecurityContextHolder.clearContext();
      filterChain.doFilter(request, response);
      return;
    }

    Integer subject = Integer.valueOf(JwtUtils.subjectFrom(token));
    Integer currentVersion = JwtUtils.versionFrom(token);
    User user = loader.loadById(subject).orElse(null);
    if (Objects.isNull(user) || !user.isTokenVersionValid(currentVersion)) {
      logger.warn("User is invalid");
      SecurityContextHolder.clearContext();
      filterChain.doFilter(request, response);
      return;
    }

    String role = JwtUtils.roleFrom(token);
    var auth =
        new UsernamePasswordAuthenticationToken(
            subject, null, List.of(new SimpleGrantedAuthority(role)));
    SecurityContextHolder.getContext().setAuthentication(auth);
    filterChain.doFilter(request, response);
  }
}
