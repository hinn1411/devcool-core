package com.devcool.application.service.channel.strategy;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
import com.devcool.domain.user.exception.UserNotFoundException;
import com.devcool.domain.user.model.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoungeCreationStrategyTest {
  private static final int CREATOR_ID = 1;
  private static final int LEADER_ID = 2;
  private static final List<Integer> MEMBER_IDS = List.of(3, 4);
  private static final List<Integer> ELEVEN_MEMBER_IDS =
      IntStream.rangeClosed(100, 110).boxed().toList();

  @Mock LoadUserPort userPort;
  @Mock ChannelPort channelPort;
  @Captor ArgumentCaptor<Channel> channelCaptor;
  LoungeCreationStrategy strategy;

  @BeforeEach
  void setUp() {
    strategy = new LoungeCreationStrategy(userPort, channelPort);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidLoungeCommands")
  void createChannel_invalidConfig_throwsAndTouchNoPort(
      String description, CreateChannelCommand command, String expectedMessage) {
    assertThatThrownBy(() -> strategy.createChannel(command))
        .isInstanceOf(InvalidChannelConfigException.class)
        .hasMessageContaining(expectedMessage);
    verifyNoInteractions(userPort, channelPort);
  }

  @ParameterizedTest(name = "{0} members")
  @ValueSource(ints = {1, 10})
  void createChannel_validMemberCount_returnsSavedChannelId(int memberCount) {
    Integer savedChannelId = 999;
    List<Integer> memberIds = IntStream.range(100, 100 + memberCount).boxed().toList();
    List<User> members = memberIds.stream().map(id -> user(id)).toList();
    when(userPort.loadByIds(memberIds)).thenReturn(members);
    when(userPort.loadById(CREATOR_ID)).thenReturn(Optional.of(user(CREATOR_ID)));
    when(channelPort.save(any())).thenReturn(savedChannelId);

    Integer actual = strategy.createChannel(lounge(ChannelType.LOUNGE, null, memberIds));

    assertThat(actual).isEqualTo(savedChannelId);
  }

  @Test
  void createChannel_duplicateMemberIds_throwsAndTouchNoPort() {
    List<Integer> duplicateMemberIds = List.of(3, 4, 3);
    CreateChannelCommand command = lounge(ChannelType.LOUNGE, null, duplicateMemberIds);

    assertThatThrownBy(() -> strategy.createChannel(command))
        .isInstanceOf(UserDuplicateException.class);

    verifyNoInteractions(userPort, channelPort);
  }

  @Test
  void createChannel_unknownMembers_throwsUserNotFoundAndSavesNothing() {
    List<Integer> requestedIds = List.of(3, 4, 5);
    when(userPort.loadByIds(requestedIds)).thenReturn(List.of(user(4)));
    CreateChannelCommand command = lounge(ChannelType.LOUNGE, null, requestedIds);

    assertThatExceptionOfType(UserNotFoundException.class)
        .isThrownBy(() -> strategy.createChannel(command))
        .satisfies(
            ex ->
                assertThat(ex.getDetails())
                    .extractingByKey("userIds", as(InstanceOfAssertFactories.LIST))
                    .containsExactlyInAnyOrder(3, 5));

    verifyNoInteractions(channelPort);
  }

  @Test
  void createChannel_validCommand_savesChannelWithMembersAndCreator() {
    List<User> members = MEMBER_IDS.stream().map(id -> user(id)).toList();
    when(userPort.loadByIds(MEMBER_IDS)).thenReturn(members);
    User creator = user(CREATOR_ID);
    when(userPort.loadById(CREATOR_ID)).thenReturn(Optional.of(creator));
    CreateChannelCommand command = lounge(ChannelType.LOUNGE, null, MEMBER_IDS);

    strategy.createChannel(command);
    verify(channelPort).save(channelCaptor.capture());
    Channel saved = channelCaptor.getValue();
    assertThat(saved.getLeader()).isNull();
    assertThat(saved.getName()).isEqualTo(command.name());
    assertThat(saved.getBoundaryType()).isEqualTo(command.boundaryType());
    assertThat(saved.getMembers()).hasSize(3);
    assertThat(saved.getMembers())
        .extracting(m -> m.getUser().getId(), Member::getRole)
        .containsExactlyInAnyOrder(
            tuple(MEMBER_IDS.get(0), MemberType.MEMBER),
            tuple(MEMBER_IDS.get(1), MemberType.MEMBER),
            tuple(CREATOR_ID, MemberType.CREATOR));
  }

  @Test
  void createChannel_creatorInMemberIds_throwsUserDuplicate() {
    List<Integer> memberIdsWithCreator = List.of(CREATOR_ID, 3, 4);
    CreateChannelCommand command = lounge(ChannelType.LOUNGE, null, memberIdsWithCreator);

    assertThatThrownBy(() -> strategy.createChannel(command))
        .isInstanceOf(UserDuplicateException.class);

    verifyNoInteractions(userPort, channelPort);
  }

  static Stream<Arguments> invalidLoungeCommands() {
    return Stream.of(
        Arguments.of(
            "Channel type is not LOUNGE",
            lounge(ChannelType.PRIVATE_CHAT, null, MEMBER_IDS),
            "Channel type must be LOUNGE"),
        Arguments.of(
            "Zero members",
            lounge(ChannelType.LOUNGE, null, List.of()),
            "Total members allowed are from 1 to 10"),
        Arguments.of(
            "Eleven members",
            lounge(ChannelType.LOUNGE, null, ELEVEN_MEMBER_IDS),
            "Total members allowed are from 1 to 10"),
        Arguments.of(
            "Has a leader",
            lounge(ChannelType.LOUNGE, LEADER_ID, MEMBER_IDS),
            "Leader is not allowed in lounge"));
  }

  static CreateChannelCommand lounge(
      ChannelType channelType, Integer leaderId, List<Integer> memberIds) {
    return new CreateChannelCommand(
        "dev-lounge",
        BoundaryType.PUBLIC,
        Instant.now(),
        channelType,
        CREATOR_ID,
        leaderId,
        memberIds);
  }

  private static User user(int id) {
    return User.builder().id(id).build();
  }
}
