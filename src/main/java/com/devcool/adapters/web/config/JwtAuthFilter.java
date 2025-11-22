package com.devcool.adapters.web.config;

import com.devcool.adapters.out.jwt.util.JwtUtils;
import com.devcool.domain.auth.out.LoadUserPort;
import com.devcool.domain.auth.out.TokenIssuerPort;
import com.devcool.domain.user.model.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

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

    String token = header.substring(7);
    if (!issuer.isAccessTokenValid(token)) {
      logger.warn("Access token is invalid!");
      SecurityContextHolder.clearContext();
      filterChain.doFilter(request, response);
      return;
    }

    String subject = JwtUtils.subjectFrom(token);
    String role = JwtUtils.roleFrom(token);
    Integer currentVersion = JwtUtils.versionFrom(token);
    User user = loader.loadById(Integer.valueOf(subject)).orElse(null);
    if (Objects.isNull(user) || currentVersion < user.getTokenVersion()) {
      SecurityContextHolder.clearContext();
      filterChain.doFilter(request, response);
      return;
    }

    var auth =
        new UsernamePasswordAuthenticationToken(
            subject, null, List.of(new SimpleGrantedAuthority(role)));
    SecurityContextHolder.getContext().setAuthentication(auth);
    filterChain.doFilter(request, response);
  }
}
