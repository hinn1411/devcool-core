package com.devcool.adapters.in.web.config;

import com.devcool.adapters.in.web.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {
  private final JwtAuthFilter jwtAuthFilter;
  private final AuthenticationEntryPoint authenticationEntryPoint;
  // API docs.
  private final String[] publicPaths = {
    "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/docs"
  };

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(publicPaths)
                    .permitAll()
                    // An allowlist: every entry needs a reason to be reachable without a token.
                    .requestMatchers(
                        "/api/v1/auth/register", // the caller has no account yet
                        "/api/v1/auth/login", // this is how a token is obtained
                        "/api/v1/auth/refresh_token", // the rt cookie authenticates the caller
                        "/public/**", // reserved for public assets; nothing is served here yet
                        "/error", // Spring's error dispatch, also for anonymous callers
                        "/ws", // WsAuthHandShakeInterceptor is the gate (ADR-0011)
                        "/actuator/health", // ALB health check and deploy smoke test: no token
                        "/actuator/health/**") // liveness/readiness; details are never shown
                    .permitAll()
                    .requestMatchers("/api/v1/auth/profile")
                    .hasAuthority("USER")
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(handling -> handling.authenticationEntryPoint(authenticationEntryPoint))
        .sessionManagement(
            session -> session.sessionCreationPolicy((SessionCreationPolicy.STATELESS)))
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }
}
