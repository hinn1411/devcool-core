package com.devcool.application.service.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.devcool.domain.channel.exception.ChannelNotFoundException;
import com.devcool.domain.channel.exception.InvalidChannelConfigException;
import com.devcool.domain.channel.model.ChannelAccessInfo;
import com.devcool.domain.channel.model.enums.ChannelType;
import com.devcool.domain.channel.port.in.command.AddMembersCommand;
import com.devcool.domain.channel.port.in.command.UpdateChannelCommand;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.common.ForbiddenException;
import com.devcool.domain.member.exception.MemberNotFoundException;
import com.devcool.domain.member.model.enums.MemberType;
import com.devcool.domain.member.port.out.MemberPort;
import com.devcool.domain.user.port.out.UserPort;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChannelServiceTest {

  private static final int CHANNEL_ID = 42;
  private static final int CALLER_ID = 7;

  @Mock private ChannelPort channelPort;
  @Mock private MemberPort memberPort;
  @Mock private UserPort userPort;

  private ChannelService newService() {
    return new ChannelService(List.of(), channelPort, memberPort, userPort);
  }

  private void channelIs(ChannelType type, int totalOfMembers) {
    when(channelPort.findAccessInfo(CHANNEL_ID))
        .thenReturn(Optional.of(new ChannelAccessInfo(type, totalOfMembers)));
  }

  private void channelIsMissing() {
    when(channelPort.findAccessInfo(CHANNEL_ID)).thenReturn(Optional.empty());
  }

  private void callerIs(MemberType role) {
    when(memberPort.findRoleOfMember(CHANNEL_ID, CALLER_ID)).thenReturn(Optional.of(role));
  }

  private void callerIsNotAMember() {
    when(memberPort.findRoleOfMember(CHANNEL_ID, CALLER_ID)).thenReturn(Optional.empty());
  }

  @Nested
  class UpdateChannel {

    private UpdateChannelCommand command() {
      return new UpdateChannelCommand(CALLER_ID, "new-name", null, null, ChannelType.FORUM);
    }

    @ParameterizedTest(name = "{0}: {1} may update")
    @CsvSource({
      "FORUM, CREATOR",
      "FORUM, LEADER",
      "LOUNGE, MEMBER",
      "LOUNGE, CREATOR",
      "PRIVATE_CHAT, MEMBER",
      "PRIVATE_CHAT, CREATOR",
    })
    void allowedCaller_updatesWithoutLoadingAggregate(ChannelType type, MemberType role) {
      channelIs(type, 3);
      callerIs(role);
      when(channelPort.update(any())).thenReturn(true);

      boolean result = newService().updateChannel(CHANNEL_ID, command());

      assertThat(result).isTrue();
      verify(channelPort).update(any());
      verify(channelPort, never()).findById(anyInt());
    }

    @Test
    void missingChannel_throwsChannelNotFoundBeforeCheckingMembership() {
      channelIsMissing();

      assertThatThrownBy(() -> newService().updateChannel(CHANNEL_ID, command()))
          .isInstanceOf(ChannelNotFoundException.class);

      verifyNoInteractions(memberPort);
      verify(channelPort, never()).update(any());
    }

    @Test
    void nonMember_throwsMemberNotFoundAndWritesNothing() {
      channelIs(ChannelType.LOUNGE, 3);
      callerIsNotAMember();

      assertThatThrownBy(() -> newService().updateChannel(CHANNEL_ID, command()))
          .isInstanceOf(MemberNotFoundException.class);

      verify(channelPort, never()).update(any());
    }

    @Test
    void forumMember_throwsForbiddenAndWritesNothing() {
      channelIs(ChannelType.FORUM, 3);
      callerIs(MemberType.MEMBER);

      assertThatThrownBy(() -> newService().updateChannel(CHANNEL_ID, command()))
          .isInstanceOf(ForbiddenException.class);

      verify(channelPort, never()).update(any());
    }

    @Test
    void channelDeletedAfterTheCheck_throwsChannelNotFound() {
      channelIs(ChannelType.LOUNGE, 3);
      callerIs(MemberType.MEMBER);
      when(channelPort.update(any())).thenReturn(false);

      assertThatThrownBy(() -> newService().updateChannel(CHANNEL_ID, command()))
          .isInstanceOf(ChannelNotFoundException.class);
    }
  }

  @Nested
  class AddMember {

    private static final Set<Integer> NEW_USER_IDS = Set.of(8, 9);

    private AddMembersCommand command() {
      return new AddMembersCommand(CALLER_ID, List.of(8, 9));
    }

    private void newUsersExistAndAreNotMembers() {
      when(userPort.findExistingUserIds(NEW_USER_IDS)).thenReturn(NEW_USER_IDS);
      when(memberPort.findMembersOfChannelByUserIds(CHANNEL_ID, NEW_USER_IDS))
          .thenReturn(List.of());
    }

    @ParameterizedTest(name = "{0}: {1} may add members")
    @CsvSource({
      "FORUM, CREATOR",
      "FORUM, LEADER",
      "LOUNGE, MEMBER",
      "LOUNGE, CREATOR",
    })
    void allowedCaller_addsMembersAndIncreasesTheCount(ChannelType type, MemberType role) {
      channelIs(type, 3);
      callerIs(role);
      newUsersExistAndAreNotMembers();
      when(memberPort.addMembers(CHANNEL_ID, NEW_USER_IDS)).thenReturn(true);

      boolean result = newService().addMember(CHANNEL_ID, command());

      assertThat(result).isTrue();
      verify(channelPort).increaseTotalMembers(CHANNEL_ID, 2);
      verify(memberPort).addMembers(CHANNEL_ID, NEW_USER_IDS);
    }

    @Test
    void missingChannel_throwsChannelNotFoundBeforeAnyOtherLookup() {
      channelIsMissing();

      assertThatThrownBy(() -> newService().addMember(CHANNEL_ID, command()))
          .isInstanceOf(ChannelNotFoundException.class);

      verifyNoInteractions(memberPort, userPort);
    }

    // The escalation chain from the audit: a stranger adding themselves to a channel.
    @Test
    void nonMember_throwsMemberNotFoundBeforeLookingUpUsers() {
      channelIs(ChannelType.PRIVATE_CHAT, 2);
      callerIsNotAMember();

      assertThatThrownBy(() -> newService().addMember(CHANNEL_ID, command()))
          .isInstanceOf(MemberNotFoundException.class);

      verifyNoInteractions(userPort);
      verify(memberPort, never()).addMembers(anyInt(), any());
      verify(channelPort, never()).increaseTotalMembers(anyInt(), anyInt());
    }

    @Test
    void forumMember_throwsForbiddenAndWritesNothing() {
      channelIs(ChannelType.FORUM, 3);
      callerIs(MemberType.MEMBER);

      assertThatThrownBy(() -> newService().addMember(CHANNEL_ID, command()))
          .isInstanceOf(ForbiddenException.class);

      verifyNoInteractions(userPort);
      verify(memberPort, never()).addMembers(anyInt(), any());
    }

    @ParameterizedTest
    @EnumSource(MemberType.class)
    void privateChat_rejectsAddingMembersForEveryRole(MemberType role) {
      channelIs(ChannelType.PRIVATE_CHAT, 2);
      callerIs(role);

      assertThatThrownBy(() -> newService().addMember(CHANNEL_ID, command()))
          .isInstanceOf(InvalidChannelConfigException.class);

      verifyNoInteractions(userPort);
      verify(memberPort, never()).addMembers(anyInt(), any());
    }

    @Test
    void lounge_fillingExactlyToElevenPeople_isAllowed() {
      channelIs(ChannelType.LOUNGE, 9);
      callerIs(MemberType.MEMBER);
      newUsersExistAndAreNotMembers();
      when(memberPort.addMembers(CHANNEL_ID, NEW_USER_IDS)).thenReturn(true);

      assertThat(newService().addMember(CHANNEL_ID, command())).isTrue();
    }

    @Test
    void lounge_exceedingElevenPeople_isRejectedAndWritesNothing() {
      channelIs(ChannelType.LOUNGE, 10);
      callerIs(MemberType.MEMBER);
      newUsersExistAndAreNotMembers();

      assertThatThrownBy(() -> newService().addMember(CHANNEL_ID, command()))
          .isInstanceOf(InvalidChannelConfigException.class);

      verify(memberPort, never()).addMembers(anyInt(), any());
      verify(channelPort, never()).increaseTotalMembers(anyInt(), anyInt());
    }

    @Test
    void forum_hasNoTotalCap() {
      channelIs(ChannelType.FORUM, 500);
      callerIs(MemberType.LEADER);
      newUsersExistAndAreNotMembers();
      when(memberPort.addMembers(CHANNEL_ID, NEW_USER_IDS)).thenReturn(true);

      assertThat(newService().addMember(CHANNEL_ID, command())).isTrue();
    }
  }
}
