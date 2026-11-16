package com.devcool.domain.channel.policy;

import com.devcool.domain.channel.model.enums.ChannelType;
import com.devcool.domain.member.model.enums.MemberType;
import java.util.Objects;

/**
 * The role table for channel management: which actions exist for a channel type, and which member
 * roles may perform them. Callers must already have established that the user is a member.
 */
public final class ChannelPermissionPolicy {

  private ChannelPermissionPolicy() {}

  /** Whether the action exists at all for this channel type. */
  public static boolean supports(ChannelType type, ChannelAction action) {
    return switch (type) {
      case ChannelType.LOUNGE -> action != ChannelAction.TRANSFER_LEADER;
      case ChannelType.FORUM -> true;
      case ChannelType.PRIVATE_CHAT -> Objects.equals(action, ChannelAction.UPDATE_CHANNEL);
    };
  }

  /** Role check only; the service checks {@code leader_id} for TRANSFER_LEADER. */
  public static boolean allows(ChannelType type, MemberType role, ChannelAction action) {
    if (!supports(type, action)) {
      return false;
    }
    // No default: a new action must get a rule
    return switch (action) {
      case UPDATE_CHANNEL, ADD_MEMBERS -> type != ChannelType.FORUM || isForumAdmin(role);
      case REMOVE_MEMBER ->
          type == ChannelType.FORUM ? isForumAdmin(role) : role == MemberType.CREATOR;
      case LEAVE_CHANNEL -> true;
      case TRANSFER_LEADER -> isForumAdmin(role);
    };
  }

  private static boolean isForumAdmin(MemberType role) {
    return Objects.equals(role, MemberType.CREATOR) || Objects.equals(role, MemberType.LEADER);
  }
}
