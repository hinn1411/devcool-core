package com.devcool.adapters.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.devcool.adapters.in.web.dto.request.AddMembersRequest;
import com.devcool.adapters.in.web.dto.request.CreateChannelRequest;
import com.devcool.adapters.in.web.dto.request.LoginRequest;
import com.devcool.adapters.in.web.dto.request.RegisterUserRequest;
import com.devcool.adapters.in.web.dto.request.UpdateChannelRequest;
import com.devcool.domain.auth.port.out.TokenIssuerPort;
import com.devcool.domain.channel.model.enums.BoundaryType;
import com.devcool.domain.channel.model.enums.ChannelType;
import com.devcool.domain.common.ErrorCode;
import com.devcool.domain.member.model.enums.MemberType;
import com.devcool.domain.user.port.out.UserPort;
import com.devcool.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.Cookie;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Authorization matrix for the channel endpoints, through the real filter chain and real tokens.
 * Not @Transactional: every request commits, as in production.
 */
class ChannelControllerAuthzIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "Secret#123";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @Autowired TokenIssuerPort tokenIssuer;
  @Autowired UserPort userPort;

  /** Who calls, relative to the seeded channel. */
  enum Caller {
    CREATOR,
    LEADER,
    MEMBER,
    NON_MEMBER
  }

  record Account(Integer id, String username, String accessToken) {}

  record LoginSession(String accessToken, String refreshCookie) {}

  /** Both member counts, kept apart so a test never compares one against the other. */
  record MemberCounts(int total, int rows) {
    MemberCounts plusOne() {
      return new MemberCounts(total + 1, rows + 1);
    }
  }

  // Fresh per test: the container is reused across runs, so never assume an empty database.
  private Account creator;
  private Account leader; // FORUM leader only
  private Account member; // plain member (the participant in a PRIVATE_CHAT)
  private Account outsider; // in none of the seeded channels
  private Account newcomer; // the user POST …/members tries to add

  @BeforeEach
  void registerUsers() throws Exception {
    creator = signUp("cr");
    leader = signUp("ld");
    member = signUp("mb");
    outsider = signUp("ou");
    newcomer = signUp("nc");
  }

  @Nested
  class PatchChannel {

    static Stream<Arguments> matrix() {
      return Stream.of(
          Arguments.of(ChannelType.LOUNGE, Caller.NON_MEMBER, 403, "MEMBER_404"),
          Arguments.of(ChannelType.LOUNGE, Caller.MEMBER, 200, "SVR_200"),
          Arguments.of(ChannelType.LOUNGE, Caller.CREATOR, 200, "SVR_200"),
          Arguments.of(ChannelType.FORUM, Caller.NON_MEMBER, 403, "MEMBER_404"),
          Arguments.of(ChannelType.FORUM, Caller.MEMBER, 403, "SVR_403"),
          Arguments.of(ChannelType.FORUM, Caller.LEADER, 200, "SVR_200"),
          Arguments.of(ChannelType.FORUM, Caller.CREATOR, 200, "SVR_200"),
          Arguments.of(ChannelType.PRIVATE_CHAT, Caller.NON_MEMBER, 403, "MEMBER_404"),
          Arguments.of(ChannelType.PRIVATE_CHAT, Caller.MEMBER, 200, "SVR_200"),
          Arguments.of(ChannelType.PRIVATE_CHAT, Caller.CREATOR, 200, "SVR_200"));
    }

    @Test
    void withoutToken() throws Exception {
      Integer channelId = seedChannel(ChannelType.LOUNGE);
      Map<String, Object> before = channelRow(channelId);

      ResultActions result = patchChannel(channelId, null, renameRequest(ChannelType.LOUNGE));

      expectUnauthenticated(result);
      assertChannelUnchanged(channelId, before);
    }

    @Test
    void withMalformedToken() throws Exception {
      Integer channelId = seedChannel(ChannelType.LOUNGE);
      Map<String, Object> before = channelRow(channelId);

      ResultActions result =
          patchChannel(channelId, "Bearer not-a-jwt", renameRequest(ChannelType.LOUNGE));

      expectUnauthenticated(result);
      assertChannelUnchanged(channelId, before);
    }

    @Test
    void withWronglySignedToken() throws Exception {
      Integer channelId = seedChannel(ChannelType.LOUNGE);
      // The creator's own claims, signed with a key the server doesn't know
      String forged = resignWithRandomKey(creator.accessToken());
      Map<String, Object> before = channelRow(channelId);

      ResultActions result =
          patchChannel(channelId, "Bearer " + forged, renameRequest(ChannelType.LOUNGE));

      expectUnauthenticated(result);
      assertChannelUnchanged(channelId, before);
    }

    @Test
    void withTokenIssuedBeforeLogout() throws Exception {
      Integer channelId = seedChannel(ChannelType.LOUNGE);
      String staleToken = tokenThenLogout(creator);
      Map<String, Object> before = channelRow(channelId);

      ResultActions result =
          patchChannel(channelId, "Bearer " + staleToken, renameRequest(ChannelType.LOUNGE));

      expectUnauthenticated(result);
      assertChannelUnchanged(channelId, before);
    }

    @ParameterizedTest(name = "{0} {1} -> {2} {3}")
    @MethodSource("matrix")
    void authenticatedCaller(
        ChannelType type, Caller caller, int expectedStatus, String expectedCode) throws Exception {
      // A fresh channel per case, so an accepted write can't leak into the next one
      Integer channelId = seedChannel(type);
      UpdateChannelRequest rename = renameRequest(type);
      Map<String, Object> before = channelRow(channelId);

      ResultActions result = patchChannel(channelId, bearer(account(caller)), rename);

      expectResponse(result, expectedStatus, expectedCode);
      if (expectedStatus == 200) {
        assertThat(channelRow(channelId)).containsEntry("name", rename.name());
      } else {
        assertChannelUnchanged(channelId, before);
      }
    }

    @Test
    void unknownChannel() throws Exception {
      // An id no sequence will reach in a test run
      Integer missingId = Integer.MAX_VALUE;

      ResultActions result =
          patchChannel(missingId, bearer(creator), renameRequest(ChannelType.LOUNGE));

      expectResponse(result, 404, ErrorCode.CHANNEL_NOT_FOUND.code());
      assertThat(channelCount(missingId)).isZero();
    }

    @Test
    void memberOfChannelA_onChannelB() throws Exception {
      // The outsider really is a member of A, and not of B; membership in A grants nothing on B
      Integer channelA = createChannel(outsider, ChannelType.LOUNGE, List.of(newcomer.id()), null);
      Integer channelB = seedChannel(ChannelType.LOUNGE);
      Map<String, Object> beforeA = channelRow(channelA);
      Map<String, Object> beforeB = channelRow(channelB);

      ResultActions result =
          patchChannel(channelB, bearer(outsider), renameRequest(ChannelType.LOUNGE));

      expectResponse(result, 403, ErrorCode.MEMBER_NOT_FOUND.code());
      assertChannelUnchanged(channelB, beforeB);
      assertChannelUnchanged(channelA, beforeA);
    }
  }

  @Nested
  class AddMembers {

    static Stream<Arguments> matrix() {
      return Stream.of(
          Arguments.of(ChannelType.LOUNGE, Caller.NON_MEMBER, 403, "MEMBER_404"),
          Arguments.of(ChannelType.LOUNGE, Caller.MEMBER, 200, "SVR_200"),
          Arguments.of(ChannelType.LOUNGE, Caller.CREATOR, 200, "SVR_200"),
          Arguments.of(ChannelType.FORUM, Caller.NON_MEMBER, 403, "MEMBER_404"),
          Arguments.of(ChannelType.FORUM, Caller.MEMBER, 403, "SVR_403"),
          Arguments.of(ChannelType.FORUM, Caller.LEADER, 200, "SVR_200"),
          Arguments.of(ChannelType.FORUM, Caller.CREATOR, 200, "SVR_200"),
          Arguments.of(ChannelType.PRIVATE_CHAT, Caller.NON_MEMBER, 403, "MEMBER_404"),
          Arguments.of(ChannelType.PRIVATE_CHAT, Caller.MEMBER, 400, "CHANNEL_400"),
          Arguments.of(ChannelType.PRIVATE_CHAT, Caller.CREATOR, 400, "CHANNEL_400"));
    }

    @Test
    void withoutToken() throws Exception {
      Integer channelId = seedChannel(ChannelType.LOUNGE);
      Map<String, Object> before = channelRow(channelId);

      ResultActions result = addMembers(channelId, null, addNewcomer());

      expectUnauthenticated(result);
      assertChannelUnchanged(channelId, before);
      assertNotMember(channelId, newcomer.id());
    }

    @Test
    void withMalformedToken() throws Exception {
      Integer channelId = seedChannel(ChannelType.LOUNGE);
      Map<String, Object> before = channelRow(channelId);

      ResultActions result = addMembers(channelId, "Bearer not-a-jwt", addNewcomer());

      expectUnauthenticated(result);
      assertChannelUnchanged(channelId, before);
      assertNotMember(channelId, newcomer.id());
    }

    @Test
    void withWronglySignedToken() throws Exception {
      Integer channelId = seedChannel(ChannelType.LOUNGE);
      String forged = resignWithRandomKey(creator.accessToken());
      Map<String, Object> before = channelRow(channelId);

      ResultActions result = addMembers(channelId, "Bearer " + forged, addNewcomer());

      expectUnauthenticated(result);
      assertChannelUnchanged(channelId, before);
      assertNotMember(channelId, newcomer.id());
    }

    @Test
    void withTokenIssuedBeforeLogout() throws Exception {
      Integer channelId = seedChannel(ChannelType.LOUNGE);
      String staleToken = tokenThenLogout(creator);
      Map<String, Object> before = channelRow(channelId);

      ResultActions result = addMembers(channelId, "Bearer " + staleToken, addNewcomer());

      expectUnauthenticated(result);
      assertChannelUnchanged(channelId, before);
      assertNotMember(channelId, newcomer.id());
    }

    @ParameterizedTest(name = "{0} {1} -> {2} {3}")
    @MethodSource("matrix")
    void authenticatedCaller(
        ChannelType type, Caller caller, int expectedStatus, String expectedCode) throws Exception {
      Integer channelId = seedChannel(type);
      MemberCounts before = memberCounts(channelId);

      ResultActions result = addMembers(channelId, bearer(account(caller)), addNewcomer());

      expectResponse(result, expectedStatus, expectedCode);
      if (expectedStatus == 200) {
        assertThat(memberRole(channelId, newcomer.id())).contains(MemberType.MEMBER.name());
        assertThat(memberCounts(channelId)).isEqualTo(before.plusOne());
      } else {
        assertNotMember(channelId, newcomer.id());
        assertThat(memberCounts(channelId)).isEqualTo(before);
      }
    }

    @Test
    void unknownChannel() throws Exception {
      Integer missingId = Integer.MAX_VALUE;
      int rowsBefore = memberCount(missingId);

      ResultActions result = addMembers(missingId, bearer(creator), addNewcomer());

      expectResponse(result, 404, ErrorCode.CHANNEL_NOT_FOUND.code());
      assertThat(memberCount(missingId)).isEqualTo(rowsBefore);
    }

    @Test
    void memberOfChannelA_addsSelfToChannelB() throws Exception {
      // The self-add attack from audit 02 §4
      Integer channelA = createChannel(outsider, ChannelType.LOUNGE, List.of(newcomer.id()), null);
      Integer channelB = seedChannel(ChannelType.LOUNGE);
      Map<String, Object> rowBeforeA = channelRow(channelA);
      MemberCounts countsBeforeA = memberCounts(channelA);
      MemberCounts countsBeforeB = memberCounts(channelB);

      ResultActions result =
          addMembers(channelB, bearer(outsider), new AddMembersRequest(List.of(outsider.id())));

      expectResponse(result, 403, ErrorCode.MEMBER_NOT_FOUND.code());
      assertNotMember(channelB, outsider.id());
      assertThat(memberCounts(channelB)).isEqualTo(countsBeforeB);
      assertChannelUnchanged(channelA, rowBeforeA);
      assertThat(memberCounts(channelA)).isEqualTo(countsBeforeA);
    }

    private AddMembersRequest addNewcomer() {
      return new AddMembersRequest(List.of(newcomer.id()));
    }
  }

  @Nested
  class GetChannels {

    @Test
    void withoutToken() throws Exception {
      ResultActions result = mvc.perform(get("/api/v1/channels"));

      expectUnauthenticated(result);
    }

    @Test
    void returnsOnlyTheCallersChannels() throws Exception {
      // The member's channels interleaved with channels they're not in, so ids of excluded
      // channels fall between included ones
      Integer mine1 = seedChannel(ChannelType.LOUNGE);
      Integer notMine1 = createChannel(outsider, ChannelType.LOUNGE, List.of(newcomer.id()), null);
      Integer mine2 = seedChannel(ChannelType.FORUM);
      Integer notMine2 =
          createChannel(outsider, ChannelType.FORUM, List.of(newcomer.id()), leader.id());
      Integer mine3 = seedChannel(ChannelType.PRIVATE_CHAT);

      ResultActions result =
          mvc.perform(get("/api/v1/channels").header(HttpHeaders.AUTHORIZATION, bearer(member)));

      MvcResult response = expectResponse(result, 200, ErrorCode.OK.code()).andReturn();
      List<Integer> ids =
          JsonPath.read(response.getResponse().getContentAsString(), "$.data.channels[*].id");
      // Newest first
      assertThat(ids).containsExactly(mine3, mine2, mine1).doesNotContain(notMine1, notMine2);
    }
  }

  private static void expectUnauthenticated(ResultActions result) throws Exception {
    result
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.code").value("AUTH_402"));
  }

  /** HTTP status, {@code $.status} and {@code $.code} all agree; returned for further chaining. */
  private static ResultActions expectResponse(ResultActions result, int status, String code)
      throws Exception {
    return result
        .andExpect(status().is(status))
        .andExpect(jsonPath("$.status").value(status))
        .andExpect(jsonPath("$.code").value(code));
  }

  private void assertChannelUnchanged(Integer channelId, Map<String, Object> before) {
    assertThat(channelRow(channelId)).isEqualTo(before);
  }

  private void assertNotMember(Integer channelId, Integer userId) {
    assertThat(memberRole(channelId, userId)).isEmpty();
  }

  /** The whole channel row, so "unchanged" covers every column, not just the name. */
  private Map<String, Object> channelRow(Integer channelId) {
    return jdbc.queryForMap("select * from channel where id = ?", channelId);
  }

  private int channelCount(Integer channelId) {
    return jdbc.queryForObject(
        "select count(*) from channel where id = ?", Integer.class, channelId);
  }

  /** Requires the channel to exist; use {@link #memberCount} for a missing one. */
  private MemberCounts memberCounts(Integer channelId) {
    int total =
        jdbc.queryForObject(
            "select total_of_members from channel where id = ?", Integer.class, channelId);
    return new MemberCounts(total, memberCount(channelId));
  }

  private int memberCount(Integer channelId) {
    return jdbc.queryForObject(
        "select count(*) from member where channel_id = ?", Integer.class, channelId);
  }

  /** The user's role in the channel, or empty when they have no member row. */
  private Optional<String> memberRole(Integer channelId, Integer userId) {
    return jdbc
        .queryForList(
            "select role from member where channel_id = ? and user_id = ?",
            String.class,
            channelId,
            userId)
        .stream()
        .findFirst();
  }

  /** Registers through the API, then issues a real token for the stored user. */
  private Account signUp(String prefix) throws Exception {
    String username = prefix + "_" + UUID.randomUUID().toString().substring(0, 10);
    var request =
        new RegisterUserRequest(username, PASSWORD, username + "@test.dev", "User " + prefix);
    MvcResult registered =
        mvc.perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();
    Integer id = JsonPath.read(registered.getResponse().getContentAsString(), "$.data.userId");
    String accessToken = tokenIssuer.issue(userPort.findById(id).orElseThrow()).accessToken();
    return new Account(id, username, accessToken);
  }

  /** Real /login, so the session has a refresh cookie that /logout accepts. */
  private LoginSession login(Account account) throws Exception {
    MvcResult loggedIn =
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(new LoginRequest(account.username(), PASSWORD))))
            .andExpect(status().isOk())
            .andReturn();
    String accessToken =
        JsonPath.read(loggedIn.getResponse().getContentAsString(), "$.data.accessToken");
    String refreshCookie =
        Objects.requireNonNull(loggedIn.getResponse().getCookie("rt")).getValue();
    return new LoginSession(accessToken, refreshCookie);
  }

  /** A token that worked (precondition: profile 200), then was logged out. */
  private String tokenThenLogout(Account account) throws Exception {
    LoginSession session = login(account);
    mvc.perform(
            get("/api/v1/auth/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken()))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken())
                .cookie(new Cookie("rt", session.refreshCookie())))
        .andExpect(status().isNoContent());
    return session.accessToken();
  }

  /** Same claims (subject, version, type, audience, expiry), signed with an unknown key. */
  private static String resignWithRandomKey(String token) throws Exception {
    byte[] otherKey = new byte[32];
    new SecureRandom().nextBytes(otherKey);
    SignedJWT forged =
        new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), SignedJWT.parse(token).getJWTClaimsSet());
    forged.sign(new MACSigner(otherKey));
    return forged.serialize();
  }

  /**
   * The channel the matrix runs against, created by {@code creator} with {@code member} in it. A
   * FORUM also gets {@code leader}.
   */
  private Integer seedChannel(ChannelType type) throws Exception {
    Integer leaderId = type == ChannelType.FORUM ? leader.id() : null;
    return createChannel(creator, type, List.of(member.id()), leaderId);
  }

  /** Creates through the API, so the creation strategies run too. */
  private Integer createChannel(
      Account owner, ChannelType type, List<Integer> memberIds, Integer leaderId) throws Exception {
    var request =
        new CreateChannelRequest(
            uniqueName(type.name().toLowerCase()),
            boundaryOf(type),
            null,
            type,
            leaderId,
            memberIds);
    MvcResult created =
        mvc.perform(
                post("/api/v1/channels")
                    .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(created.getResponse().getContentAsString(), "$.data.channelId");
  }

  /** A valid body that changes only the name, so validation can't be what rejects a request. */
  private static UpdateChannelRequest renameRequest(ChannelType type) {
    return new UpdateChannelRequest(uniqueName("renamed"), boundaryOf(type), null, type);
  }

  private ResultActions patchChannel(
      Integer channelId, String authorization, UpdateChannelRequest body) throws Exception {
    return mvc.perform(
        withAuth(patch("/api/v1/channels/{id}", channelId), authorization)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body)));
  }

  private ResultActions addMembers(Integer channelId, String authorization, AddMembersRequest body)
      throws Exception {
    return mvc.perform(
        withAuth(post("/api/v1/channels/{id}/members", channelId), authorization)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body)));
  }

  /** {@code authorization == null} sends no Authorization header at all. */
  private static MockHttpServletRequestBuilder withAuth(
      MockHttpServletRequestBuilder request, String authorization) {
    return authorization == null
        ? request
        : request.header(HttpHeaders.AUTHORIZATION, authorization);
  }

  private Account account(Caller caller) {
    return switch (caller) {
      case CREATOR -> creator;
      case LEADER -> leader;
      case MEMBER -> member;
      case NON_MEMBER -> outsider;
    };
  }

  private static String bearer(Account account) {
    return "Bearer " + account.accessToken();
  }

  private static BoundaryType boundaryOf(ChannelType type) {
    return type == ChannelType.PRIVATE_CHAT ? BoundaryType.PRIVATE : BoundaryType.PUBLIC;
  }

  private static String uniqueName(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }
}
