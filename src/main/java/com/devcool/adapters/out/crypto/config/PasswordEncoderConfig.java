package com.devcool.adapters.out.crypto.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Not in the web-only SecurityConfig: the password hasher is needed in every mode. */
@Configuration
public class PasswordEncoderConfig {

  /** 12 in every deployed profile; the test profile lowers it (each +1 doubles the cost). */
  @Bean
  PasswordEncoder passwordEncoder(@Value("${security.bcrypt.strength:12}") int strength) {
    return new BCryptPasswordEncoder(strength);
  }
}
