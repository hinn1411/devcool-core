package com.devcool.adapters.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devcool.adapters.in.web.dto.mapper.ChannelDtoMapperImpl;
import com.devcool.domain.auth.port.out.TokenIssuerPort;
import com.devcool.domain.channel.port.in.CreateChannelUseCase;
import com.devcool.domain.channel.port.in.GetChannelQuery;
import com.devcool.domain.channel.port.in.UpdateChannelUseCase;
import com.devcool.domain.channel.port.in.command.AddMembersCommand;
import com.devcool.domain.channel.port.in.command.UpdateChannelCommand;
import com.devcool.domain.common.ForbiddenException;
import com.devcool.domain.user.port.out.UserPort;
import java.security.Principal;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web slice: the real controller, exception handler and Jackson, with the use cases mocked.
 * Security filters are off, so the caller is supplied as the request principal; authentication
 * itself is covered by the integration tests in P1-T12.
 */
@WebMvcTest(ChannelController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ChannelDtoMapperImpl.class)
class ChannelControllerTest {

  private static final Principal CALLER = new TestingAuthenticationToken("7", null);
  private static final String UPDATE_BODY =
      """
      {"name": "renamed-room", "boundaryType": "PUBLIC", "channelType": "LOUNGE"}
      """;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private UpdateChannelUseCase channelUpdater;
  @MockitoBean private CreateChannelUseCase channelCreator;
  @MockitoBean private GetChannelQuery channelQuerier;

  // JwtAuthFilter is still created as a bean in the slice, so its ports need stand-ins.
  @MockitoBean private TokenIssuerPort tokenIssuerPort;
  @MockitoBean private UserPort userPort;

  @Test
  void updateChannel_passesTheAuthenticatedCallerIdToTheUseCase() throws Exception {
    when(channelUpdater.updateChannel(eq(42), any())).thenReturn(true);

    mockMvc
        .perform(
            patch("/api/v1/channels/42")
                .principal(CALLER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(UPDATE_BODY))
        .andExpect(status().isOk());

    ArgumentCaptor<UpdateChannelCommand> command =
        ArgumentCaptor.forClass(UpdateChannelCommand.class);
    verify(channelUpdater).updateChannel(eq(42), command.capture());
    assertThat(command.getValue().callerId()).isEqualTo(7);
  }

  @Test
  void addMembers_passesTheAuthenticatedCallerIdToTheUseCase() throws Exception {
    when(channelUpdater.addMember(eq(42), any())).thenReturn(true);

    mockMvc
        .perform(
            post("/api/v1/channels/42/members")
                .principal(CALLER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userIds\": [8, 9]}"))
        .andExpect(status().isOk());

    ArgumentCaptor<AddMembersCommand> command = ArgumentCaptor.forClass(AddMembersCommand.class);
    verify(channelUpdater).addMember(eq(42), command.capture());
    assertThat(command.getValue().callerId()).isEqualTo(7);
    assertThat(command.getValue().userIds()).containsExactly(8, 9);
  }

  // A client cannot name the caller: an id in the body is not part of the request contract.
  @Test
  void addMembers_ignoresACallerIdSentInTheBody() throws Exception {
    when(channelUpdater.addMember(eq(42), any())).thenReturn(true);

    mockMvc
        .perform(
            post("/api/v1/channels/42/members")
                .principal(CALLER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"callerId\": 1, \"userIds\": [8]}"))
        .andExpect(status().isOk());

    ArgumentCaptor<AddMembersCommand> command = ArgumentCaptor.forClass(AddMembersCommand.class);
    verify(channelUpdater).addMember(eq(42), command.capture());
    assertThat(command.getValue().callerId()).isEqualTo(7);
  }

  @Test
  void addMembers_moreThanFiftyIds_returns422WithoutCallingTheUseCase() throws Exception {
    String fiftyOneIds =
        IntStream.rangeClosed(1, 51).mapToObj(String::valueOf).collect(Collectors.joining(","));

    mockMvc
        .perform(
            post("/api/v1/channels/42/members")
                .principal(CALLER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userIds\": [" + fiftyOneIds + "]}"))
        .andExpect(status().isUnprocessableEntity());

    verifyNoInteractions(channelUpdater);
  }

  @Test
  void updateChannel_forbiddenCaller_returns403() throws Exception {
    when(channelUpdater.updateChannel(eq(42), any()))
        .thenThrow(new ForbiddenException("Only the creator or the leader may do this"));

    mockMvc
        .perform(
            patch("/api/v1/channels/42")
                .principal(CALLER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(UPDATE_BODY))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("SVR_403"));
  }
}
