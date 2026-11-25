package com.devcool.application.service.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.devcool.domain.channel.exception.InvalidChannelConfigException;
import com.devcool.domain.channel.model.Channel;
import com.devcool.domain.channel.model.DirectChat;
import com.devcool.domain.channel.model.DirectChatPair;
import com.devcool.domain.channel.model.enums.BoundaryType;
import com.devcool.domain.channel.model.enums.ChannelType;
import com.devcool.domain.channel.port.in.command.OpenDirectChatCommand;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.channel.port.out.DirectChatPort;
import com.devcool.domain.member.model.Member;
import com.devcool.domain.member.model.enums.MemberType;
import com.devcool.domain.user.exception.UserNotFoundException;
import com.devcool.domain.user.model.User;
import com.devcool.domain.user.model.enums.UserStatus;
import com.devcool.domain.user.port.out.UserPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

@ExtendWith(MockitoExtension.class)
class DirectChatServiceTest {

  private static final long CALLER = 1L;
  private static final long TARGET = 2L;
  private static final long CHANNEL = 55L;
  private static final DirectChatPair PAIR = new DirectChatPair(CALLER, TARGET);
  private static final Instant NOW = Instant.parse("2026-11-17T09:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  @Mock UserPort userPort;
  @Mock ChannelPort channelPort;
  @Mock DirectChatPort directChatPort;
  @Captor ArgumentCaptor<Channel> channelCaptor;

  private final CountingTransactions transactions = new CountingTransactions();
  private DirectChatService service;

  @BeforeEach
  void setUp() {
    service = new DirectChatService(userPort, channelPort, directChatPort, transactions, CLOCK);
  }

  @Test
  void noDirectChatYet_createsAPrivateChatWithBothUsers() {
    when(directChatPort.findChannelId(PAIR)).thenReturn(Optional.empty());
    when(userPort.findById(TARGET)).thenReturn(Optional.of(user(TARGET, UserStatus.ACTIVE)));
    when(userPort.findById(CALLER)).thenReturn(Optional.of(user(CALLER, UserStatus.ACTIVE)));
    when(channelPort.save(any())).thenReturn(CHANNEL);
    when(directChatPort.insert(PAIR, CHANNEL)).thenReturn(true);

    DirectChat result = service.openDirectChat(new OpenDirectChatCommand(CALLER, TARGET));

    assertThat(result).isEqualTo(new DirectChat(CHANNEL, true));
    verify(channelPort).save(channelCaptor.capture());
    Channel saved = channelCaptor.getValue();
    assertThat(saved.getChannelType()).isEqualTo(ChannelType.PRIVATE_CHAT);
    assertThat(saved.getBoundaryType()).isEqualTo(BoundaryType.PRIVATE);
    assertThat(saved.getTotalOfMembers()).isEqualTo(2);
    assertThat(saved.getMembers())
        .extracting(m -> m.getUser().getId(), Member::getRole)
        .containsExactlyInAnyOrder(
            tuple(CALLER, MemberType.CREATOR), tuple(TARGET, MemberType.MEMBER));
    assertThat(saved.getMembers()).extracting(Member::getJoinedTime).containsOnly(NOW);
    assertThat(transactions.rolledBack).isZero();
  }

  @Test
  void existingDirectChat_isReturnedWithoutCreatingAnything() {
    when(directChatPort.findChannelId(PAIR)).thenReturn(Optional.of(CHANNEL));

    DirectChat result = service.openDirectChat(new OpenDirectChatCommand(CALLER, TARGET));

    assertThat(result).isEqualTo(new DirectChat(CHANNEL, false));
    verify(channelPort, never()).save(any());
    verify(directChatPort, never()).insert(any(), any());
  }

  @Test
  void existingDirectChat_isFoundFromTheOtherSideToo() {
    when(directChatPort.findChannelId(PAIR)).thenReturn(Optional.of(CHANNEL));

    DirectChat result = service.openDirectChat(new OpenDirectChatCommand(TARGET, CALLER));

    assertThat(result).isEqualTo(new DirectChat(CHANNEL, false));
  }

  @Test
  void withYourself_isRejectedBeforeAnyLookup() {
    assertThatThrownBy(() -> service.openDirectChat(new OpenDirectChatCommand(CALLER, CALLER)))
        .isInstanceOf(InvalidChannelConfigException.class);
    verifyNoInteractions(userPort, channelPort, directChatPort);
  }

  @Test
  void unknownTarget_isNotFound_andNothingIsCreated() {
    when(directChatPort.findChannelId(PAIR)).thenReturn(Optional.empty());
    when(userPort.findById(TARGET)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.openDirectChat(new OpenDirectChatCommand(CALLER, TARGET)))
        .isInstanceOf(UserNotFoundException.class);
    verify(channelPort, never()).save(any());
  }

  @ParameterizedTest
  @EnumSource(
      value = UserStatus.class,
      names = {"DELETED", "BLOCKED"})
  void inactiveTarget_isNotFound_andNothingIsCreated(UserStatus status) {
    when(directChatPort.findChannelId(PAIR)).thenReturn(Optional.empty());
    when(userPort.findById(TARGET)).thenReturn(Optional.of(user(TARGET, status)));

    assertThatThrownBy(() -> service.openDirectChat(new OpenDirectChatCommand(CALLER, TARGET)))
        .isInstanceOf(UserNotFoundException.class);
    verify(channelPort, never()).save(any());
  }

  @Test
  void inactiveTarget_withAnExistingDirectChat_stillGetsItBack() {
    when(directChatPort.findChannelId(PAIR)).thenReturn(Optional.of(CHANNEL));

    DirectChat result = service.openDirectChat(new OpenDirectChatCommand(CALLER, TARGET));

    assertThat(result).isEqualTo(new DirectChat(CHANNEL, false));
    verifyNoInteractions(userPort);
  }

  @Test
  void lostPairRace_rollsBackItsChannel_andReturnsTheWinner() {
    long winner = 77L;
    when(directChatPort.findChannelId(PAIR))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(winner));
    when(userPort.findById(TARGET)).thenReturn(Optional.of(user(TARGET, UserStatus.ACTIVE)));
    when(userPort.findById(CALLER)).thenReturn(Optional.of(user(CALLER, UserStatus.ACTIVE)));
    when(channelPort.save(any())).thenReturn(CHANNEL);
    when(directChatPort.insert(PAIR, CHANNEL)).thenReturn(false);

    DirectChat result = service.openDirectChat(new OpenDirectChatCommand(CALLER, TARGET));

    assertThat(result).isEqualTo(new DirectChat(winner, false));
    // The loser's channel and members must not commit, or the pair keeps a stray second DM
    assertThat(transactions.rolledBack).isEqualTo(1);
  }

  private static User user(long id, UserStatus status) {
    return User.builder().id(id).status(status).build();
  }

  private static final class CountingTransactions implements TransactionOperations {
    int rolledBack;

    @Override
    public <T> T execute(TransactionCallback<T> action) {
      TransactionStatus status = new SimpleTransactionStatus();
      T result = action.doInTransaction(status);
      if (status.isRollbackOnly()) {
        rolledBack++;
      }
      return result;
    }
  }
}
