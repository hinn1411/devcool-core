package com.devcool.adapters.in.web.security;

import com.devcool.domain.auth.model.AccessClaims;
import com.devcool.domain.auth.port.out.TokenIssuerPort;
import com.devcool.domain.user.model.User;
import com.devcool.domain.user.port.out.UserPort;
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
  private final TokenIssuerPort tokenIssuerPort;
  private final UserPort userPort;

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
    AccessClaims claims = tokenIssuerPort.verifyAccess(token).orElse(null);
    if (Objects.isNull(claims)) {
      logger.warn("Access token is invalid!");
      SecurityContextHolder.clearContext();
      filterChain.doFilter(request, response);
      return;
    }

    Integer subject = claims.userId();
    User user = userPort.findById(subject).orElse(null);
    if (Objects.isNull(user) || !user.isTokenVersionValid(claims.tokenVersion())) {
      logger.warn("User is invalid");
      SecurityContextHolder.clearContext();
      filterChain.doFilter(request, response);
      return;
    }

    String role = claims.role();
    var auth =
        new UsernamePasswordAuthenticationToken(
            subject, null, List.of(new SimpleGrantedAuthority(role)));
    SecurityContextHolder.getContext().setAuthentication(auth);
    filterChain.doFilter(request, response);
  }
}
