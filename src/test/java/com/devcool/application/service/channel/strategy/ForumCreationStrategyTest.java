package com.devcool.application.service.channel.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
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
import com.devcool.domain.user.exception.UserNotFoundException;
import com.devcool.domain.user.model.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
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
class ForumCreationStrategyTest {

  private static final int CREATOR_ID = 1;
  private static final int LEADER_ID = 2;
  private static final List<Integer> VALID_MEMBER_IDS = List.of(3, 4);
  private static final Instant SOME_EXPIRY = Instant.parse("2030-01-01T00:00:00Z");

  @Mock LoadUserPort userPort;
  @Mock ChannelPort channelPort;
  @Captor ArgumentCaptor<Channel> channelCaptor;

  ForumCreationStrategy strategy;

  @BeforeEach
  void setUp() {
    strategy = new ForumCreationStrategy(userPort, channelPort);
  }

  static Stream<Arguments> invalidForumCommands() {
    return Stream.of(
        Arguments.of(
            "not a forum",
            forum(ChannelType.LOUNGE, LEADER_ID, VALID_MEMBER_IDS, null),
            "Channel type must be FORUM"),
        Arguments.of(
            "zero members",
            forum(ChannelType.FORUM, LEADER_ID, List.of(), null),
            "Total members allowed are from 1 to 10"),
        Arguments.of(
            "eleven members",
            forum(ChannelType.FORUM, LEADER_ID, memberIds(11), null),
            "Total members allowed are from 1 to 10"),
        Arguments.of(
            "no leader",
            forum(ChannelType.FORUM, null, VALID_MEMBER_IDS, null),
            "leader must be present"),
        Arguments.of(
            "has an expiry",
            forum(ChannelType.FORUM, LEADER_ID, VALID_MEMBER_IDS, SOME_EXPIRY),
            "must not have expired time"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidForumCommands")
  void createChannel_withInvalidConfig_throwsAndTouchesNoPort(
      String description, CreateChannelCommand command, String expectedMessage) {
    assertThatThrownBy(() -> strategy.createChannel(command))
        .isInstanceOf(InvalidChannelConfigException.class)
        .hasMessageContaining(expectedMessage);

    verifyNoInteractions(userPort, channelPort);
  }

  @Test
  void createChannel_withNonexistentLeader_throwsUserNotFoundAndSavesNothing() {
    CreateChannelCommand command = forum(ChannelType.FORUM, LEADER_ID, VALID_MEMBER_IDS, null);
    when(userPort.loadById(CREATOR_ID)).thenReturn(Optional.of(user(CREATOR_ID)));
    when(userPort.loadById(LEADER_ID)).thenReturn(Optional.empty());

    assertThatExceptionOfType(UserNotFoundException.class)
        .isThrownBy(() -> strategy.createChannel(command))
        .satisfies(ex -> assertThat(ex.getDetails()).containsEntry("userId", LEADER_ID));
    verifyNoInteractions(channelPort);
  }

  @ParameterizedTest(name = "{0} members")
  @ValueSource(ints = {1, 10})
  void createChannel_withValidMemberCount_returnsSavedChannelId(int memberCount) {
    Integer savedChannelId = 101;
    List<Integer> memberIds = memberIds(memberCount);
    when(userPort.loadByIds(memberIds)).thenReturn(memberIds.stream().map(id -> user(id)).toList());
    when(userPort.loadById(CREATOR_ID)).thenReturn(Optional.of(user(CREATOR_ID)));
    when(userPort.loadById(LEADER_ID)).thenReturn(Optional.of(user(LEADER_ID)));
    when(channelPort.save(any())).thenReturn(savedChannelId);
    CreateChannelCommand command = forum(ChannelType.FORUM, LEADER_ID, memberIds, null);
    Integer actual = strategy.createChannel(command);

    assertThat(actual).isEqualTo(savedChannelId);
  }

  // SPEC DECISION (membership): a user can only send messages in a channel they are a Member of,
  // so the saved forum needs a Member row for the creator and the leader, not just for memberIds.
  //
  // - One row per distinct person; totalOfMembers is the number of rows.
  // - creator == leader is one person: one row, role CREATOR (the creator is always the single
  //   CREATOR, as in Lounge).
  // - The leader's role is deliberately NOT asserted. Leader permissions are not designed yet, so
  //   any role would lock in an arbitrary choice.
  // - Overlap between memberIds and creator/leader is unspecified, so it is not tested.
  static Stream<Arguments> creatorAndLeaderCases() {
    return Stream.of(
        Arguments.of(
            "leader is a different user", LEADER_ID, List.of(CREATOR_ID, LEADER_ID, 3, 4), 4),
        Arguments.of("leader is the creator", CREATOR_ID, List.of(CREATOR_ID, 3, 4), 3));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("creatorAndLeaderCases")
  void createChannel_savesOneMemberRowPerPerson(
      String description, int leaderId, List<Integer> expectedUserIds, int expectedTotal) {
    // Any id resolves to a user with that id, so the ids in the saved members show who was loaded.
    when(userPort.loadByIds(VALID_MEMBER_IDS))
        .thenReturn(VALID_MEMBER_IDS.stream().map(id -> user(id)).toList());
    when(userPort.loadById(any())).thenAnswer(call -> Optional.of(user(call.getArgument(0))));

    strategy.createChannel(forum(ChannelType.FORUM, leaderId, VALID_MEMBER_IDS, null));

    verify(channelPort).save(channelCaptor.capture());
    Channel saved = channelCaptor.getValue();
    assertThat(saved.getMembers())
        .extracting(member -> member.getUser().getId())
        .containsExactlyInAnyOrderElementsOf(expectedUserIds);
    assertThat(saved.getMembers())
        .extracting(member -> member.getUser().getId(), Member::getRole)
        .contains(
            tuple(3, MemberType.MEMBER),
            tuple(4, MemberType.MEMBER),
            tuple(CREATOR_ID, MemberType.CREATOR));
    assertThat(saved.getTotalOfMembers()).isEqualTo(expectedTotal);
  }

  private static User user(int id) {
    return User.builder().id(id).build();
  }

  private static CreateChannelCommand forum(
      ChannelType type, Integer leaderId, List<Integer> memberIds, Instant expiredTime) {
    return new CreateChannelCommand(
        "dev-forum", BoundaryType.PUBLIC, expiredTime, type, CREATOR_ID, leaderId, memberIds);
  }

  private static List<Integer> memberIds(int count) {
    return IntStream.rangeClosed(10, 10 + count - 1).boxed().toList();
  }
}
