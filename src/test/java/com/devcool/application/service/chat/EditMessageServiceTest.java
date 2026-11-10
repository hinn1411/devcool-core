package com.devcool.application.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devcool.application.service.channel.ChannelAuthorizer;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.chat.exception.InvalidMessageConfigException;
import com.devcool.domain.chat.exception.MessageEditWindowClosedException;
import com.devcool.domain.chat.exception.MessageNotFoundException;
import com.devcool.domain.chat.exception.MessageVersionConflictException;
import com.devcool.domain.chat.model.EditableMessage;
import com.devcool.domain.chat.model.EditedMessage;
import com.devcool.domain.chat.model.enums.ContentType;
import com.devcool.domain.chat.port.in.command.EditMessageCommand;
import com.devcool.domain.chat.port.out.MessagePort;
import com.devcool.domain.common.ForbiddenException;
import com.devcool.domain.member.port.out.MemberPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Edit rules (P3-T06): sender-only, 24 h window, text/markdown, client-version guard. */
@ExtendWith(MockitoExtension.class)
class EditMessageServiceTest {

  private static final long MESSAGE = 500L, CHANNEL = 1L, SEQ = 9L, ALICE = 10L, BOB = 11L;
  private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");
  private static final Instant EDITED_BEFORE = NOW.minusSeconds(600);
  private static final int CURRENT = 3;

  @Mock private MessagePort messagePort;
  @Mock private ChannelPort channelPort;
  @Mock private MemberPort memberPort;

  private EditMessageService service;

  @BeforeEach
  void setUp() {
    service =
        new EditMessageService(
            messagePort,
            new ChannelAuthorizer(channelPort, memberPort),
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  // ---- 1. happy path ---------------------------------------------------------------------------

  @Test
  void sender_currentVersion_within24h_replacesAndBumps() {
    stored(ContentType.TEXT, NOW.minus(Duration.ofHours(1)));
    when(messagePort.updateContent(MESSAGE, CURRENT, "new", NOW)).thenReturn(true);

    EditedMessage edited = service.edit(edit(ALICE, "new", CURRENT));

    verify(messagePort).updateContent(MESSAGE, CURRENT, "new", NOW);
    assertThat(edited)
        .isEqualTo(
            new EditedMessage(MESSAGE, CHANNEL, SEQ, ContentType.TEXT, "new", CURRENT + 1, NOW));
  }

  @Test
  void markdownMessage_canBeEdited() {
    stored(ContentType.MARKDOWN, NOW.minus(Duration.ofHours(1)));
    when(messagePort.updateContent(MESSAGE, CURRENT, "# new", NOW)).thenReturn(true);

    assertThat(service.edit(edit(ALICE, "# new", CURRENT)).version()).isEqualTo(CURRENT + 1);
  }

  // ---- 2. version guard ------------------------------------------------------------------------

  @Test
  void staleVersion_conflicts_withoutUpdate() {
    stored(ContentType.TEXT, NOW.minus(Duration.ofHours(1)));

    assertThatThrownBy(() -> service.edit(edit(ALICE, "new", CURRENT - 1)))
        .isInstanceOf(MessageVersionConflictException.class);
    neverUpdated();
  }

  @Test
  void lostRace_conflicts() {
    // Read v3, but another edit committed v4 before our conditional UPDATE ran.
    stored(ContentType.TEXT, NOW.minus(Duration.ofHours(1)));
    when(messagePort.updateContent(MESSAGE, CURRENT, "new", NOW)).thenReturn(false);

    assertThatThrownBy(() -> service.edit(edit(ALICE, "new", CURRENT)))
        .isInstanceOf(MessageVersionConflictException.class);
  }

  // ---- 3. 24 h window --------------------------------------------------------------------------

  @Test
  void windowClosed_atExactly24h() {
    stored(ContentType.TEXT, NOW.minus(Duration.ofHours(24)));

    assertThatThrownBy(() -> service.edit(edit(ALICE, "new", CURRENT)))
        .isInstanceOf(MessageEditWindowClosedException.class);
    neverUpdated();
  }

  @Test
  void justBefore24h_isAccepted() {
    stored(ContentType.TEXT, NOW.minus(Duration.ofHours(24)).plusSeconds(1));
    when(messagePort.updateContent(MESSAGE, CURRENT, "new", NOW)).thenReturn(true);

    assertThat(service.edit(edit(ALICE, "new", CURRENT)).version()).isEqualTo(CURRENT + 1);
  }

  // ---- 4. type and content ---------------------------------------------------------------------

  @ParameterizedTest
  @EnumSource(
      value = ContentType.class,
      names = {"IMAGE", "VIDEO"})
  void mediaMessage_isRejected(ContentType type) {
    stored(type, NOW.minus(Duration.ofHours(1)));

    assertThatThrownBy(() -> service.edit(edit(ALICE, "new", CURRENT)))
        .isInstanceOf(InvalidMessageConfigException.class);
    neverUpdated();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " \n\t ", "hi\0there"})
  void invalidContent_isRejected(String content) {
    stored(ContentType.TEXT, NOW.minus(Duration.ofHours(1)));

    assertThatThrownBy(() -> service.edit(edit(ALICE, content, CURRENT)))
        .isInstanceOf(InvalidMessageConfigException.class);
    neverUpdated();
  }

  @Test
  void tooLongContent_isRejected() {
    stored(ContentType.TEXT, NOW.minus(Duration.ofHours(1)));

    assertThatThrownBy(() -> service.edit(edit(ALICE, "x".repeat(1001), CURRENT)))
        .isInstanceOf(InvalidMessageConfigException.class);
    neverUpdated();
  }

  // ---- 5. unchanged content --------------------------------------------------------------------

  @Test
  void sameContent_isNoOp_noBump() {
    stored(ContentType.TEXT, NOW.minus(Duration.ofHours(1)));

    EditedMessage edited = service.edit(edit(ALICE, "old", CURRENT));

    assertThat(edited)
        .isEqualTo(
            new EditedMessage(
                MESSAGE, CHANNEL, SEQ, ContentType.TEXT, "old", CURRENT, EDITED_BEFORE));
    neverUpdated();
  }

  @Test
  void sameContent_staleVersion_stillConflicts() {
    stored(ContentType.TEXT, NOW.minus(Duration.ofHours(1)));

    assertThatThrownBy(() -> service.edit(edit(ALICE, "old", CURRENT - 1)))
        .isInstanceOf(MessageVersionConflictException.class);
  }

  // ---- 6. authorization ------------------------------------------------------------------------

  @Test
  void memberNotSender_isForbidden() {
    when(messagePort.findForEdit(MESSAGE))
        .thenReturn(Optional.of(message(ContentType.TEXT, NOW.minus(Duration.ofHours(1)))));
    when(memberPort.existMemberOfChannelByUserId(CHANNEL, BOB)).thenReturn(true);

    assertThatThrownBy(() -> service.edit(edit(BOB, "new", CURRENT)))
        .isInstanceOf(ForbiddenException.class);
    neverUpdated();
  }

  @Test
  void nonMember_isNotFound() {
    // Alice sent it but has left the channel: same answer as an unknown id.
    when(messagePort.findForEdit(MESSAGE))
        .thenReturn(Optional.of(message(ContentType.TEXT, NOW.minus(Duration.ofHours(1)))));
    when(memberPort.existMemberOfChannelByUserId(CHANNEL, ALICE)).thenReturn(false);

    assertThatThrownBy(() -> service.edit(edit(ALICE, "new", CURRENT)))
        .isInstanceOf(MessageNotFoundException.class);
    neverUpdated();
  }

  @Test
  void unknownOrDeleted_isNotFound() {
    when(messagePort.findForEdit(MESSAGE)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.edit(edit(ALICE, "new", CURRENT)))
        .isInstanceOf(MessageNotFoundException.class);
    neverUpdated();
  }

  // ---- helpers ---------------------------------------------------------------------------------

  /** Alice's message with content "old" at {@link #CURRENT}; Alice is still a member. */
  private void stored(ContentType type, Instant createdTime) {
    when(messagePort.findForEdit(MESSAGE)).thenReturn(Optional.of(message(type, createdTime)));
    when(memberPort.existMemberOfChannelByUserId(CHANNEL, ALICE)).thenReturn(true);
  }

  private static EditableMessage message(ContentType type, Instant createdTime) {
    return new EditableMessage(
        MESSAGE, CHANNEL, SEQ, ALICE, type, "old", CURRENT, createdTime, EDITED_BEFORE);
  }

  private static EditMessageCommand edit(long userId, String content, int version) {
    return new EditMessageCommand(userId, MESSAGE, content, version);
  }

  private void neverUpdated() {
    verify(messagePort, never()).updateContent(anyLong(), anyInt(), anyString(), any());
  }
}
