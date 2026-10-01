package com.devcool.adapters.in.web.controller;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devcool.adapters.in.web.dto.mapper.UserDtoMapperImpl;
import com.devcool.domain.auth.port.out.LoadUserPort;
import com.devcool.domain.auth.port.out.TokenIssuerPort;
import com.devcool.domain.user.exception.UserNotFoundException;
import com.devcool.domain.user.model.User;
import com.devcool.domain.user.model.enums.Role;
import com.devcool.domain.user.model.enums.UserStatus;
import com.devcool.domain.user.port.in.GetUserQuery;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web slice: the real controller, exception handler and Jackson, with the use case mocked. Security
 * filters are off because this test is about the response body; authentication is covered by the
 * integration tests in P1-T12.
 */
@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(UserDtoMapperImpl.class)
class UserControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private GetUserQuery getUser;

  // JwtAuthFilter is still created as a bean in the slice, so its ports need stand-ins.
  @MockitoBean private TokenIssuerPort tokenIssuer;
  @MockitoBean private LoadUserPort loadUser;

  @Test
  void getProfile_exposesOnlyPublicFields() throws Exception {
    when(getUser.byId(101)).thenReturn(userWithEveryFieldSet());

    mockMvc
        .perform(get("/api/v1/users/101"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").exists())
        .andExpect(jsonPath("$.data.username").exists())
        .andExpect(jsonPath("$.data.name").exists())
        .andExpect(jsonPath("$.data.avatar").exists())
        .andExpect(jsonPath("$.data.password").doesNotExist())
        .andExpect(jsonPath("$.data.tokenVersion").doesNotExist())
        .andExpect(jsonPath("$.data.email").doesNotExist())
        .andExpect(jsonPath("$.data.role").doesNotExist())
        .andExpect(jsonPath("$.data.status").doesNotExist())
        .andExpect(jsonPath("$.data.lastLoginTime").doesNotExist())
        .andExpect(jsonPath("$.data.emailVerified").doesNotExist());
  }

  // Allowlist: fails when any field is added to the response, including ones not named above.
  @Test
  void getProfile_returnsExactlyTheFourPublicFieldsWithTheirValues() throws Exception {
    when(getUser.byId(101)).thenReturn(userWithEveryFieldSet());

    mockMvc
        .perform(get("/api/v1/users/101"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data", aMapWithSize(4)))
        .andExpect(jsonPath("$.data.id").value("101"))
        .andExpect(jsonPath("$.data.username").value("hien_giang"))
        .andExpect(jsonPath("$.data.name").value("Hien Giang"))
        .andExpect(jsonPath("$.data.avatar").value("https://cdn.devcool.com/avatars/hien.png"));
  }

  @Test
  void getProfile_unknownUser_returns404WithUserNotFoundCode() throws Exception {
    when(getUser.byId(999)).thenThrow(new UserNotFoundException(999));

    mockMvc
        .perform(get("/api/v1/users/999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("USR_404"));
  }

  private static User userWithEveryFieldSet() {
    return User.builder()
        .id(101)
        .username("hien_giang")
        .password("$2a$12$abcdefghijklmnopqrstuuJ9wq1Yb0mQ0m0m0m0m0m0m0m0m0m0m")
        .email("hien@example.com")
        .emailVerified(true)
        .name("Hien Giang")
        .avatar("https://cdn.devcool.com/avatars/hien.png")
        .role(Role.USER)
        .status(UserStatus.ACTIVE)
        .lastLoginTime(Instant.parse("2025-10-12T15:30:00Z"))
        .tokenVersion(7)
        .build();
  }
}
