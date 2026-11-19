package com.devcool.adapters.in.web.controller;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.devcool.domain.auth.port.out.TokenIssuerPort;
import com.devcool.domain.user.port.out.UserPort;
import com.devcool.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Seeding and request helpers shared by the search ITs (P3-T13). No config annotations, so the
 * cached context is shared with every other IT.
 */
abstract class AbstractMessageSearchIT extends AbstractIntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired TokenIssuerPort tokenIssuer;
  @Autowired UserPort userPort;

  /** Letters only (one {@code simple} lexeme) and unique, so other ITs' rows never match. */
  static String uniqueWord() {
    StringBuilder word = new StringBuilder("zq");
    ThreadLocalRandom random = ThreadLocalRandom.current();
    for (int i = 0; i < 10; i++) {
      word.append((char) ('a' + random.nextInt(26)));
    }
    return word.toString();
  }

  ResultActions perform(long user, MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request.header(AUTHORIZATION, bearer(user)));
  }

  static MockHttpServletRequestBuilder channelSearch(long channelId, String q) {
    return get("/api/v1/channels/{id}/messages/search", channelId).param("q", q);
  }

  static MockHttpServletRequestBuilder globalSearch(String q) {
    return get("/api/v1/messages/search").param("q", q);
  }

  static List<Long> longs(ResultActions result, String path) throws Exception {
    List<Number> values =
        JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
    return values.stream().map(Number::longValue).toList();
  }

  static List<Long> ids(ResultActions result) throws Exception {
    return longs(result, "$.data.items[*].id");
  }

  private String bearer(long user) {
    return "Bearer " + tokenIssuer.issue(userPort.findById(user).orElseThrow()).accessToken();
  }

  // ---- seeding ---------------------------------------------------------------------------------

  long user() {
    String name = "s" + UUID.randomUUID().toString().substring(0, 8);
    return jdbc.queryForObject(
        """
        insert into app_user (id, user_name, email, role, status, token_version)
        values (nextval('app_user_seq'), ?, ?, 'USER', 'ACTIVE', 0) returning id
        """,
        Long.class,
        name,
        name + "@test.dev");
  }

  /** A LOUNGE with {@code creator} as CREATOR and {@code members} as MEMBER. */
  long channel(long creator, long... members) {
    long id =
        jdbc.queryForObject(
            """
            insert into channel (id, name, boundary, total_of_members, channel_type)
            values (nextval('channel_seq'), 'search-it', 'PRIVATE', ?, 'LOUNGE') returning id
            """,
            Long.class,
            1 + members.length);
    addMember(id, creator, "CREATOR");
    for (long member : members) {
      addMember(id, member, "MEMBER");
    }
    return id;
  }

  void leave(long channelId, long user) {
    jdbc.update("delete from member where channel_id = ? and user_id = ?", channelId, user);
  }

  /** A TEXT message with the channel's next seq. */
  long post(long channelId, long sender, String content) {
    return insert(channelId, sender, content, "TEXT");
  }

  long postMarkdown(long channelId, long sender, String content) {
    return insert(channelId, sender, content, "MARKDOWN");
  }

  /** A media message whose content (e.g. a caption or file name) carries the word anyway. */
  long postMedia(long channelId, long sender, String content) {
    return insert(channelId, sender, content, "IMAGE");
  }

  void softDelete(long messageId) {
    jdbc.update("update message set deleted_time = now() where id = ?", messageId);
  }

  long seqOf(long messageId) {
    return jdbc.queryForObject("select seq from message where id = ?", Long.class, messageId);
  }

  private void addMember(long channelId, long user, String role) {
    jdbc.update(
        """
        insert into member (id, user_id, channel_id, role, joined_time)
        values (nextval('member_seq'), ?, ?, ?, now())
        """,
        user,
        channelId,
        role);
  }

  private long insert(long channelId, long sender, String content, String type) {
    long seq =
        jdbc.queryForObject(
            "update channel set last_seq = last_seq + 1 where id = ? returning last_seq",
            Long.class,
            channelId);
    return jdbc.queryForObject(
        """
        insert into message (id, user_id, channel_id, content, content_type, created_time, seq)
        values (nextval('message_seq'), ?, ?, ?, ?, now(), ?) returning id
        """,
        Long.class,
        sender,
        channelId,
        content,
        type,
        seq);
  }
}
