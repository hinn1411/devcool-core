package com.devcool.application.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.devcool.application.service.channel.ChannelAuthorizer;
import com.devcool.domain.channel.exception.ChannelNotFoundException;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.chat.exception.InvalidSearchQueryException;
import com.devcool.domain.chat.model.MessageItem;
import com.devcool.domain.chat.model.MessageList;
import com.devcool.domain.chat.model.ReactionSummary;
import com.devcool.domain.chat.port.in.command.SearchChannelMessagesCommand;
import com.devcool.domain.chat.port.in.command.SearchMyMessagesCommand;
import com.devcool.domain.chat.port.out.MessagePort;
import com.devcool.domain.chat.port.out.ReactionPort;
import com.devcool.domain.member.exception.MemberNotFoundException;
import com.devcool.domain.member.port.out.MemberPort;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** P3-T13. */
@ExtendWith(MockitoExtension.class)
class SearchMessagesServiceTest {

  private static final long CHANNEL = 1L, ALICE = 10L;
  private static final String Q = "deploy";

  @Mock private MessagePort messagePort;
  @Mock private ReactionPort reactionPort;
  @Mock private ChannelPort channelPort;
  @Mock private MemberPort memberPort;

  private SearchMessagesService service;

  @BeforeEach
  void setUp() {
    service =
        new SearchMessagesService(
            messagePort, reactionPort, new ChannelAuthorizer(channelPort, memberPort));
  }

  /** Done when 3. */
  static Stream<String> invalidQueries() {
    return Stream.of(null, "", "   ", "\t\n", "a".repeat(201));
  }

  /** Done when 1, 2, 3, 7. */
  @Nested
  class ChannelSearch {

    @Test
    void member_isCheckedBeforeTheSearch_whichAsksForOneExtraRow() {
      aliceCanRead();
      when(messagePort.searchInChannel(Q, CHANNEL, null, 21)).thenReturn(ids(3, 2, 1));

      MessageList page = service.searchInChannel(channel(Q, null, 20));

      assertThat(page.items()).extracting(MessageItem::getId).containsExactly(3L, 2L, 1L);
      assertThat(page.hasMore()).isFalse();
      assertThat(page.cursorId()).isNull();
      InOrder order = inOrder(memberPort, messagePort);
      order.verify(memberPort).existMemberOfChannelByUserId(CHANNEL, ALICE);
      order.verify(messagePort).searchInChannel(Q, CHANNEL, null, 21);
    }

    @Test
    void extraRow_meansHasMore_andIsDropped() {
      aliceCanRead();
      when(messagePort.searchInChannel(Q, CHANNEL, 9L, 3)).thenReturn(ids(8, 5, 2));

      MessageList page = service.searchInChannel(channel(Q, 9L, 2));

      assertThat(page.items()).extracting(MessageItem::getId).containsExactly(8L, 5L);
      assertThat(page.hasMore()).isTrue();
      assertThat(page.cursorId()).isEqualTo(5L);
    }

    /** Review #4. */
    @Test
    void cursor_isTheLastSeq_notTheId() {
      aliceCanRead();
      List<MessageItem> hits =
          List.of(
              MessageItem.builder().id(1301L).seq(5L).build(),
              MessageItem.builder().id(1240L).seq(4L).build(),
              MessageItem.builder().id(1102L).seq(2L).build());
      when(messagePort.searchInChannel(Q, CHANNEL, null, 3)).thenReturn(hits);

      MessageList page = service.searchInChannel(channel(Q, null, 2));

      assertThat(page.cursorId()).isEqualTo(4L);
    }

    @Test
    void exactly200Characters_isSearched() {
      aliceCanRead();
      String longest = "a".repeat(200);
      when(messagePort.searchInChannel(longest, CHANNEL, null, 21)).thenReturn(List.of());

      assertThat(service.searchInChannel(channel(longest, null, 20)).items()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("com.devcool.application.service.chat.SearchMessagesServiceTest#invalidQueries")
    void invalidQuery_throws_andTouchesNoPort(String q) {
      SearchChannelMessagesCommand command = channel(q, null, 20);

      assertThatThrownBy(() -> service.searchInChannel(command))
          .isInstanceOf(InvalidSearchQueryException.class);
      verifyNoInteractions(channelPort, memberPort, messagePort, reactionPort);
    }

    @Test
    void nonMember_searchesNothing() {
      when(channelPort.existById(CHANNEL)).thenReturn(true);
      when(memberPort.existMemberOfChannelByUserId(CHANNEL, ALICE)).thenReturn(false);
      SearchChannelMessagesCommand command = channel(Q, null, 20);

      assertThatThrownBy(() -> service.searchInChannel(command))
          .isInstanceOf(MemberNotFoundException.class);
      verifyNoInteractions(messagePort, reactionPort);
    }

    @Test
    void missingChannel_searchesNothing() {
      when(channelPort.existById(CHANNEL)).thenReturn(false);
      SearchChannelMessagesCommand command = channel(Q, null, 20);

      assertThatThrownBy(() -> service.searchInChannel(command))
          .isInstanceOf(ChannelNotFoundException.class);
      verifyNoInteractions(messagePort, reactionPort);
    }

    @Test
    void reactions_areSummarizedOnceForThePage() {
      aliceCanRead();
      when(messagePort.searchInChannel(Q, CHANNEL, null, 3)).thenReturn(ids(7, 4, 1));
      ReactionSummary thumbs = new ReactionSummary("👍", 1, true);
      when(reactionPort.summarize(any(), any())).thenReturn(Map.of(7L, List.of(thumbs)));

      MessageList page = service.searchInChannel(channel(Q, null, 2));

      assertReactionsAttachedForPageOnly(page, thumbs);
    }

    private void aliceCanRead() {
      when(channelPort.existById(CHANNEL)).thenReturn(true);
      when(memberPort.existMemberOfChannelByUserId(CHANNEL, ALICE)).thenReturn(true);
    }

    private static SearchChannelMessagesCommand channel(String q, Long beforeSeq, int limit) {
      return new SearchChannelMessagesCommand(ALICE, CHANNEL, q, beforeSeq, limit);
    }
  }

  /** Done when 3, 6, 7. */
  @Nested
  class GlobalSearch {

    @Test
    void searchesTheCallersChannels_inOneQuery_withoutPerChannelChecks() {
      when(messagePort.searchInMyChannels(Q, ALICE, null, 21)).thenReturn(ids(9, 6));

      MessageList page = service.searchMyChannels(mine(Q, null, 20));

      assertThat(page.items()).extracting(MessageItem::getId).containsExactly(9L, 6L);
      assertThat(page.hasMore()).isFalse();
      verifyNoInteractions(channelPort, memberPort);
    }

    @Test
    void extraRow_meansHasMore_andCursorIsTheLastId() {
      when(messagePort.searchInMyChannels(Q, ALICE, 500L, 3)).thenReturn(ids(40, 30, 20));

      MessageList page = service.searchMyChannels(mine(Q, 500L, 2));

      assertThat(page.items()).extracting(MessageItem::getId).containsExactly(40L, 30L);
      assertThat(page.hasMore()).isTrue();
      assertThat(page.cursorId()).isEqualTo(30L);
    }

    @ParameterizedTest
    @MethodSource("com.devcool.application.service.chat.SearchMessagesServiceTest#invalidQueries")
    void invalidQuery_throws_andTouchesNoPort(String q) {
      SearchMyMessagesCommand command = mine(q, null, 20);

      assertThatThrownBy(() -> service.searchMyChannels(command))
          .isInstanceOf(InvalidSearchQueryException.class);
      verifyNoInteractions(messagePort, reactionPort);
    }

    @Test
    void reactions_areSummarizedOnceForThePage() {
      when(messagePort.searchInMyChannels(Q, ALICE, null, 3)).thenReturn(ids(7, 4, 1));
      ReactionSummary thumbs = new ReactionSummary("🎉", 3, false);
      when(reactionPort.summarize(any(), any())).thenReturn(Map.of(7L, List.of(thumbs)));

      MessageList page = service.searchMyChannels(mine(Q, null, 2));

      assertReactionsAttachedForPageOnly(page, thumbs);
    }

    private static SearchMyMessagesCommand mine(String q, Long beforeId, int limit) {
      return new SearchMyMessagesCommand(ALICE, q, beforeId, limit);
    }
  }

  /** Page of ids 7, 4 (the extra row 1 dropped); only 7 has a reaction. */
  private void assertReactionsAttachedForPageOnly(MessageList page, ReactionSummary expected) {
    ArgumentCaptor<List<Long>> asked = ArgumentCaptor.captor();
    verify(reactionPort).summarize(asked.capture(), eq(ALICE));
    assertThat(asked.getValue()).containsExactlyInAnyOrder(7L, 4L);
    assertThat(page.items().getFirst().getReactions()).containsExactly(expected);
    assertThat(page.items().getLast().getReactions()).isEmpty();
  }

  private static List<MessageItem> ids(long... ids) {
    return Arrays.stream(ids).mapToObj(id -> MessageItem.builder().id(id).seq(id).build()).toList();
  }
}
