package com.devcool.application.service.channel;

import com.devcool.domain.channel.exception.ChannelNotFoundException;
import com.devcool.domain.channel.exception.InvalidChannelConfigException;
import com.devcool.domain.channel.model.ChannelAccessInfo;
import com.devcool.domain.channel.model.enums.ChannelType;
import com.devcool.domain.channel.policy.ChannelAction;
import com.devcool.domain.channel.policy.ChannelPermissionPolicy;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.common.ForbiddenException;
import com.devcool.domain.member.exception.MemberNotFoundException;
import com.devcool.domain.member.model.enums.MemberType;
import com.devcool.domain.member.port.out.MemberPort;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Channel access checks shared by the services that read or write channel data. Not a use case:
 * only services call it, so it has no inbound port. Each method passes or throws a domain
 * exception.
 */
@Component
@RequiredArgsConstructor
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Spring injects the ports; they are shared stateless beans, not owned state")
public class ChannelAuthorizer {

  private static final Logger log = LoggerFactory.getLogger(ChannelAuthorizer.class);
  private final ChannelPort channelPort;
  private final MemberPort memberPort;

  /**
   * Channel exists → caller is a member. The channel is checked first so an unknown channel reports
   * {@link ChannelNotFoundException} rather than being masked by the membership lookup.
   */
  public void requireMember(Long channelId, Long userId) {
    if (!channelPort.existById(channelId)) {
      log.warn("Channel id {} does not exist", channelId);
      throw new ChannelNotFoundException(channelId);
    }
    requireMembership(channelId, userId);
  }

  /**
   * Caller is a member, without checking the channel first: a member row can't exist without its
   * channel (FK), so an unknown channel also reports {@link MemberNotFoundException}.
   */
  public void requireMembership(Long channelId, Long userId) {
    if (!memberPort.existMemberOfChannelByUserId(channelId, userId)) {
      log.warn("User {} is not a member of channel {}", userId, channelId);
      throw new MemberNotFoundException(userId);
    }
  }

  /**
   * Channel exists → caller is a member → the action exists for this channel type → the caller's
   * role may perform it. Returns the channel's access info for the checks that follow.
   */
  public ChannelAccessInfo requireAllowed(Long channelId, Long callerId, ChannelAction action) {

    ChannelAccessInfo channelInfo =
        channelPort
            .findAccessInfo(channelId)
            .orElseThrow(() -> new ChannelNotFoundException(channelId));
    ChannelType type = channelInfo.channelType();
    MemberType callerRole =
        memberPort
            .findRoleOfMember(channelId, callerId)
            .orElseThrow(() -> new MemberNotFoundException(callerId));
    if (!ChannelPermissionPolicy.supports(type, action)) {
      log.warn("Action {} not allowed in channel type {}", action, channelInfo.channelType());
      throw new InvalidChannelConfigException("Action not supported");
    }

    if (!ChannelPermissionPolicy.allows(type, callerRole, action)) {
      log.warn(
          "Role {} cannot perform action {} in {}", callerRole, action, channelInfo.channelType());
      throw new ForbiddenException("Action not allowed");
    }

    return channelInfo;
  }
}
