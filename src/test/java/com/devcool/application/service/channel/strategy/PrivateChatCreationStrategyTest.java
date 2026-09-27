package com.devcool.application.service.channel.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.devcool.domain.auth.port.out.LoadUserPort;
import com.devcool.domain.channel.exception.InvalidChannelConfigException;
import com.devcool.domain.channel.model.Channel;
import com.devcool.domain.channel.model.enums.BoundaryType;
import com.devcool.domain.channel.model.enums.ChannelType;
import com.devcool.domain.channel.port.in.command.CreateChannelCommand;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.member.model.Member;
import com.devcool.domain.member.model.enums.MemberType;
import com.devcool.domain.user.exception.UserDuplicateException;
import com.devcool.domain.user.model.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrivateChatCreationStrategyTest {

  private static final int CREATOR_ID = 1;
  private static final int OTHER_ID = 2;
  private static final int THIRD_ID = 3;
  private static final int LEADER_ID = 4;
  private static final List<Integer> VALID_MEMBER_IDS = List.of(OTHER_ID);
  private static final List<Integer> TWO_MEMBER_IDS = List.of(OTHER_ID, THIRD_ID);
  private static final Instant SOME_EXPIRY = Instant.parse("2030-01-01T00:00:00Z");

  @Mock LoadUserPort userPort;
  @Mock ChannelPort channelPort;
  @Captor ArgumentCaptor<Channel> channelCaptor;

  PrivateChatCreationStrategy strategy;

  @BeforeEach
  void setUp() {
    strategy = new PrivateChatCreationStrategy(userPort, channelPort);
  }

  static Stream<Arguments> invalidPrivateChatCommands() {
    return Stream.of(
        Arguments.of(
            "not a private chat",
            privateChat(ChannelType.LOUNGE, BoundaryType.PRIVATE, null, VALID_MEMBER_IDS, null),
            "Channel type must be PRIVATE_CHAT"),
        Arguments.of(
            "has a leader",
            privateChat(
                ChannelType.PRIVATE_CHAT, BoundaryType.PRIVATE, LEADER_ID, VALID_MEMBER_IDS, null),
            "Private chat does not have leader"),
        Arguments.of(
            "boundary is not PRIVATE",
            privateChat(
                ChannelType.PRIVATE_CHAT, BoundaryType.PUBLIC, null, VALID_MEMBER_IDS, null),
            "Private chat visibility must be PRIVATE"),
        Arguments.of(
            "zero members",
            privateChat(ChannelType.PRIVATE_CHAT, BoundaryType.PRIVATE, null, List.of(), null),
            "Private chat only has 1 member"),
        Arguments.of(
            "two members",
            privateChat(ChannelType.PRIVATE_CHAT, BoundaryType.PRIVATE, null, TWO_MEMBER_IDS, null),
            "Private chat only has 1 member"),
        Arguments.of(
            "has an expiry",
            privateChat(
                ChannelType.PRIVATE_CHAT,
                BoundaryType.PRIVATE,
                null,
                VALID_MEMBER_IDS,
                SOME_EXPIRY),
            "must not have expired time"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidPrivateChatCommands")
  void createChannel_invalidConfig_throwsAndTouchesNoPort(
      String description, CreateChannelCommand command, String expectedMessage) {
    assertThatThrownBy(() -> strategy.createChannel(command))
        .isInstanceOf(InvalidChannelConfigException.class)
        .hasMessageContaining(expectedMessage);
    verifyNoInteractions(userPort, channelPort);
  }

  @Test
  void createChannel_creatorInMemberIds_throwsUserDuplicate() {
    CreateChannelCommand command =
        privateChat(
            ChannelType.PRIVATE_CHAT, BoundaryType.PRIVATE, null, List.of(CREATOR_ID), null);

    assertThatThrownBy(() -> strategy.createChannel(command))
        .isInstanceOf(UserDuplicateException.class);
    verifyNoInteractions(userPort, channelPort);
  }

  @Test
  void createChannel_validCommand_savesChannelWithBothParticipants() {
    Integer savedChannelId = 555;
    User other = user(OTHER_ID);
    User creator = user(CREATOR_ID);
    when(userPort.loadByIds(VALID_MEMBER_IDS)).thenReturn(List.of(other));
    when(userPort.loadById(CREATOR_ID)).thenReturn(Optional.of(creator));
    when(channelPort.save(any())).thenReturn(savedChannelId);
    CreateChannelCommand command =
        privateChat(ChannelType.PRIVATE_CHAT, BoundaryType.PRIVATE, null, VALID_MEMBER_IDS, null);

    Integer actual = strategy.createChannel(command);
    assertThat(actual).isEqualTo(savedChannelId);
    verify(channelPort).save(channelCaptor.capture());
    Channel saved = channelCaptor.getValue();
    assertThat(saved.getMembers()).hasSize(saved.getTotalOfMembers());
    assertThat(saved.getMembers())
        .extracting(m -> m.getUser().getId(), Member::getRole)
        .containsExactlyInAnyOrder(
            tuple(CREATOR_ID, MemberType.CREATOR), tuple(OTHER_ID, MemberType.MEMBER));
  }

  private static CreateChannelCommand privateChat(
      ChannelType channelType,
      BoundaryType boundaryType,
      Integer leaderId,
      List<Integer> memberIds,
      Instant expiredTime) {
    return new CreateChannelCommand(
        "dev-private-chat",
        boundaryType,
        expiredTime,
        channelType,
        CREATOR_ID,
        leaderId,
        memberIds);
  }

  private static User user(int id) {
    return User.builder().id(id).build();
  }
}
