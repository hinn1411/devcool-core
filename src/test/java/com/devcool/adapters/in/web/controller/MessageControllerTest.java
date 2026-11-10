package com.devcool.adapters.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devcool.adapters.in.web.dto.mapper.MessageDtoMapperImpl;
import com.devcool.domain.auth.port.out.TokenIssuerPort;
import com.devcool.domain.channel.exception.ChannelNotFoundException;
import com.devcool.domain.chat.exception.InvalidHistoryCursorException;
import com.devcool.domain.chat.model.MessageItem;
import com.devcool.domain.chat.model.MessageList;
import com.devcool.domain.chat.model.enums.ContentType;
import com.devcool.domain.chat.port.in.GetMessageQuery;
import com.devcool.domain.chat.port.in.command.GetMessageCommand;
import com.devcool.domain.member.exception.MemberNotFoundException;
import com.devcool.domain.user.port.out.UserPort;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** History endpoint web slice (P3-T05): params, validation, error statuses. */
@WebMvcTest(MessageController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(MessageDtoMapperImpl.class)
class MessageControllerTest {

  private static final Principal CALLER = new TestingAuthenticationToken("7", null);
  private static final String HISTORY = "/api/v1/channels/42/messages";
  private static final MessageList EMPTY = new MessageList(List.of(), null, false);

  @Autowired private MockMvc mockMvc;

  @MockitoBean private GetMessageQuery messageQuerier;

  // JwtAuthFilter is still created as a bean in the slice, so its ports need stand-ins.
  @MockitoBean private TokenIssuerPort tokenIssuerPort;
  @MockitoBean private UserPort userPort;

  @Test
  void beforeSeq_reachesTheQuery() throws Exception {
    when(messageQuerier.getMessages(any())).thenReturn(EMPTY);

    mockMvc
        .perform(get(HISTORY + "?beforeSeq=9&limit=5").principal(CALLER))
        .andExpect(status().isOk());

    assertThat(sentCommand()).isEqualTo(new GetMessageCommand(7L, 42L, null, 9L, null, 5));
  }

  @Test
  void afterSeq_reachesTheQuery() throws Exception {
    when(messageQuerier.getMessages(any())).thenReturn(EMPTY);

    mockMvc.perform(get(HISTORY + "?afterSeq=41").principal(CALLER)).andExpect(status().isOk());

    assertThat(sentCommand()).isEqualTo(new GetMessageCommand(7L, 42L, null, null, 41L, 20));
  }

  @Test
  void responseItems_carrySeq() throws Exception {
    MessageItem item =
        MessageItem.builder()
            .id(500L)
            .seq(3L)
            .content("hi")
            .contentType(ContentType.TEXT)
            .createdTime(Instant.parse("2026-11-01T10:00:00Z"))
            .userId(7L)
            .build();
    when(messageQuerier.getMessages(any())).thenReturn(new MessageList(List.of(item), null, false));

    mockMvc
        .perform(get(HISTORY).principal(CALLER))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].seq").value(3));
  }

  @Test
  void twoCursors_returns400() throws Exception {
    when(messageQuerier.getMessages(any()))
        .thenThrow(new InvalidHistoryCursorException("Use only one cursor"));

    mockMvc
        .perform(get(HISTORY + "?beforeSeq=9&afterSeq=2").principal(CALLER))
        .andExpect(status().isBadRequest());
  }

  @ParameterizedTest
  @ValueSource(strings = {"limit=0", "limit=101", "beforeSeq=0", "afterSeq=-1"})
  void invalidBounds_returns422(String query) throws Exception {
    mockMvc
        .perform(get(HISTORY + "?" + query).principal(CALLER))
        .andExpect(status().isUnprocessableEntity());

    verifyNoInteractions(messageQuerier);
  }

  @Test
  void nonMember_returns403() throws Exception {
    when(messageQuerier.getMessages(any())).thenThrow(new MemberNotFoundException(7L));

    mockMvc.perform(get(HISTORY).principal(CALLER)).andExpect(status().isForbidden());
  }

  @Test
  void missingChannel_returns404() throws Exception {
    when(messageQuerier.getMessages(any())).thenThrow(new ChannelNotFoundException(42L));

    mockMvc.perform(get(HISTORY).principal(CALLER)).andExpect(status().isNotFound());
  }

  private GetMessageCommand sentCommand() {
    ArgumentCaptor<GetMessageCommand> command = ArgumentCaptor.forClass(GetMessageCommand.class);
    verify(messageQuerier).getMessages(command.capture());
    return command.getValue();
  }
}
