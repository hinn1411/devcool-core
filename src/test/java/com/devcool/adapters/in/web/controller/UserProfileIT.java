package com.devcool.adapters.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devcool.adapters.in.web.dto.request.LoginRequest;
import com.devcool.adapters.in.web.dto.request.RegisterUserRequest;
import com.devcool.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Profile JSON shape against a real stored user (audit #1, deferred from P1-T04). Unlike the
 * {@code @WebMvcTest}, the user here is loaded from Postgres with a real BCrypt hash and token
 * version, so a mapping regression would leak real values.
 */
class UserProfileIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "Secret#123";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;

  record Account(Integer id, String username) {}

  @Test
  void anotherUsersProfile_exposesOnlyPublicFields() throws Exception {
    Account viewer = register();
    Account target = register();
    assertSecretsAreStored(target);

    Map<String, Object> data =
        getData("/api/v1/users/" + target.id(), accessToken(viewer)); // BOLA shape: A reads B

    // Allowlist: any field outside it fails, including ones added later
    assertThat(data.keySet()).isSubsetOf("id", "username", "name", "avatar");
    assertThat(data)
        .containsEntry("id", String.valueOf(target.id()))
        .containsEntry("username", target.username());
  }

  @Test
  void ownProfile_neverExposesPasswordOrTokenVersion() throws Exception {
    Account me = register();
    assertSecretsAreStored(me);

    Map<String, Object> data = getData("/api/v1/auth/profile", accessToken(me));

    assertThat(data).containsEntry("username", me.username());
    assertThat(data.keySet()).doesNotContain("password", "tokenVersion");
  }

  /** Precondition: the secrets exist in the row, so "absent from the JSON" means something. */
  private void assertSecretsAreStored(Account account) {
    Map<String, Object> row =
        jdbc.queryForMap("select password, token_version from app_user where id = ?", account.id());
    assertThat(row.get("password")).asString().startsWith("$2"); // BCrypt
    assertThat(row.get("token_version")).isNotNull();
  }

  private Map<String, Object> getData(String path, String accessToken) throws Exception {
    String body =
        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.data");
  }

  private Account register() throws Exception {
    // user_name is varchar(20): "up_" + 10 chars
    String username = "up_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    var request = new RegisterUserRequest(username, PASSWORD, username + "@test.dev", "Profile");
    String body =
        mvc.perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    Integer id = JsonPath.read(body, "$.data.userId");
    return new Account(id, username);
  }

  private String accessToken(Account account) throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(new LoginRequest(account.username(), PASSWORD))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.data.accessToken");
  }
}
