package com.devcool.application.service.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.devcool.domain.channel.exception.ChannelNotFoundException;
import com.devcool.domain.channel.exception.InvalidChannelConfigException;
import com.devcool.domain.channel.model.ChannelAccessInfo;
import com.devcool.domain.channel.model.enums.ChannelType;
import com.devcool.domain.channel.policy.ChannelAction;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.common.ForbiddenException;
import com.devcool.domain.member.exception.MemberNotFoundException;
import com.devcool.domain.member.model.enums.MemberType;
import com.devcool.domain.member.port.out.MemberPort;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChannelAuthorizerTest {

  private static final int CHANNEL_ID = 42;
  private static final int USER_ID = 7;

  @Mock private ChannelPort channelPort;
  @Mock private MemberPort memberPort;

  private ChannelAuthorizer authorizer;

  @BeforeEach
  void setUp() {
    authorizer = new ChannelAuthorizer(channelPort, memberPort);
  }

  @Nested
  class RequireMember {

    @Test
    void member_passes() {
      when(channelPort.existById(CHANNEL_ID)).thenReturn(true);
      when(memberPort.existMemberOfChannelByUserId(CHANNEL_ID, USER_ID)).thenReturn(true);

      assertThatCode(() -> authorizer.requireMember(CHANNEL_ID, USER_ID))
          .doesNotThrowAnyException();
    }

    @Test
    void missingChannel_throwsChannelNotFoundBeforeCheckingMembership() {
      when(channelPort.existById(CHANNEL_ID)).thenReturn(false);

      assertThatThrownBy(() -> authorizer.requireMember(CHANNEL_ID, USER_ID))
          .isInstanceOf(ChannelNotFoundException.class);

      verifyNoInteractions(memberPort);
    }

    @Test
    void nonMember_throwsMemberNotFound() {
      when(channelPort.existById(CHANNEL_ID)).thenReturn(true);
      when(memberPort.existMemberOfChannelByUserId(CHANNEL_ID, USER_ID)).thenReturn(false);

      assertThatThrownBy(() -> authorizer.requireMember(CHANNEL_ID, USER_ID))
          .isInstanceOf(MemberNotFoundException.class);
    }
  }

  @Nested
  class RequireMembership {

    @Test
    void member_passesWithoutLookingUpTheChannel() {
      when(memberPort.existMemberOfChannelByUserId(CHANNEL_ID, USER_ID)).thenReturn(true);

      assertThatCode(() -> authorizer.requireMembership(CHANNEL_ID, USER_ID))
          .doesNotThrowAnyException();

      verifyNoInteractions(channelPort);
    }

    @Test
    void nonMember_throwsMemberNotFound() {
      when(memberPort.existMemberOfChannelByUserId(CHANNEL_ID, USER_ID)).thenReturn(false);

      assertThatThrownBy(() -> authorizer.requireMembership(CHANNEL_ID, USER_ID))
          .isInstanceOf(MemberNotFoundException.class);
    }
  }

  /** The full role matrix is exercised through {@code ChannelServiceTest}. */
  @Nested
  class RequireAllowed {

    private void channelIs(ChannelType type) {
      when(channelPort.findAccessInfo(CHANNEL_ID))
          .thenReturn(Optional.of(new ChannelAccessInfo(type, 3)));
    }

    private void callerIs(MemberType role) {
      when(memberPort.findRoleOfMember(CHANNEL_ID, USER_ID)).thenReturn(Optional.of(role));
    }

    @Test
    void allowedRole_returnsTheChannelAccessInfo() {
      channelIs(ChannelType.FORUM);
      callerIs(MemberType.CREATOR);

      ChannelAccessInfo info =
          authorizer.requireAllowed(CHANNEL_ID, USER_ID, ChannelAction.ADD_MEMBERS);

      assertThat(info).isEqualTo(new ChannelAccessInfo(ChannelType.FORUM, 3));
    }

    @Test
    void missingChannel_throwsChannelNotFoundBeforeCheckingMembership() {
      when(channelPort.findAccessInfo(CHANNEL_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(
              () -> authorizer.requireAllowed(CHANNEL_ID, USER_ID, ChannelAction.UPDATE_CHANNEL))
          .isInstanceOf(ChannelNotFoundException.class);

      verifyNoInteractions(memberPort);
    }

    @Test
    void nonMember_throwsMemberNotFound() {
      channelIs(ChannelType.LOUNGE);
      when(memberPort.findRoleOfMember(CHANNEL_ID, USER_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(
              () -> authorizer.requireAllowed(CHANNEL_ID, USER_ID, ChannelAction.UPDATE_CHANNEL))
          .isInstanceOf(MemberNotFoundException.class);
    }

    @Test
    void unsupportedAction_throwsInvalidChannelConfig() {
      channelIs(ChannelType.PRIVATE_CHAT);
      callerIs(MemberType.CREATOR);

      assertThatThrownBy(
              () -> authorizer.requireAllowed(CHANNEL_ID, USER_ID, ChannelAction.ADD_MEMBERS))
          .isInstanceOf(InvalidChannelConfigException.class);
    }

    @Test
    void wrongRole_throwsForbidden() {
      channelIs(ChannelType.FORUM);
      callerIs(MemberType.MEMBER);

      assertThatThrownBy(
              () -> authorizer.requireAllowed(CHANNEL_ID, USER_ID, ChannelAction.UPDATE_CHANNEL))
          .isInstanceOf(ForbiddenException.class);
    }
  }
}
