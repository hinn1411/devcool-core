package com.devcool.adapters.in.web.controller;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devcool.domain.auth.port.out.TokenIssuerPort;
import com.devcool.domain.user.port.out.UserPort;
import com.devcool.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Presigned upload over HTTP with real tokens (P3-T14). The happy path signs with AWS credentials
 * CI doesn't have, so it lives in MediaControllerTest; here: authz and the OpenAPI contract.
 */
class MediaControllerIT extends AbstractIntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired TokenIssuerPort tokenIssuer;
  @Autowired UserPort userPort;

  private long outsider;
  private long channel;

  @BeforeEach
  void seed() {
    long alice = user();
    outsider = user();
    channel = channel(alice);
  }

  @Test
  void presignUpload_nonMember_is403() throws Exception {
    mvc.perform(
            post("/api/v1/medias/presign-upload")
                .header(AUTHORIZATION, bearer(outsider))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"channelId": %d, "contentType": "image/png", "size": 1024}
                    """
                        .formatted(channel)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("MEMBER_404"));
  }

  @Test
  void presignUpload_withoutToken_is401() throws Exception {
    mvc.perform(
            post("/api/v1/medias/presign-upload")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"channelId\": 1, \"contentType\": \"image/png\", \"size\": 1}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void apiDocs_markUploadDeprecated_andListPresignUpload() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/medias/upload'].post.deprecated").value(true))
        .andExpect(jsonPath("$.paths['/api/v1/medias/presign-upload'].post").exists());
  }

  // ---- helpers ---------------------------------------------------------------------------------

  private String bearer(long user) {
    return "Bearer " + tokenIssuer.issue(userPort.findById(user).orElseThrow()).accessToken();
  }

  private long user() {
    String name = "u" + UUID.randomUUID().toString().substring(0, 8);
    return jdbc.queryForObject(
        """
        insert into app_user (id, user_name, email, role, status, token_version)
        values (nextval('app_user_seq'), ?, ?, 'USER', 'ACTIVE', 0) returning id
        """,
        Long.class,
        name,
        name + "@test.dev");
  }

  private long channel(long creator) {
    long id =
        jdbc.queryForObject(
            """
            insert into channel (id, name, boundary, total_of_members, channel_type, last_seq)
            values (nextval('channel_seq'), 'media-it', 'PRIVATE', 1, 'LOUNGE', 0) returning id
            """,
            Long.class);
    jdbc.update(
        """
        insert into member (id, user_id, channel_id, role, joined_time)
        values (nextval('member_seq'), ?, ?, 'CREATOR', now())
        """,
        creator,
        id);
    return id;
  }
}
