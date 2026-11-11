package com.devcool.domain.chat.policy;

import com.devcool.domain.channel.model.enums.ChannelType;
import com.devcool.domain.member.model.enums.MemberType;
import java.util.Objects;

/** Who may delete a message: the sender, a LOUNGE/FORUM CREATOR, or a FORUM LEADER. */
public final class MessageDeletePolicy {

  private MessageDeletePolicy() {}

  public static boolean mayDelete(boolean isSender, ChannelType channelType, MemberType role) {
    if (isSender) {
      return true;
    }

    return switch (channelType) {
      case ChannelType.PRIVATE_CHAT -> false;
      case ChannelType.LOUNGE -> Objects.equals(role, MemberType.CREATOR);
      case ChannelType.FORUM ->
          Objects.equals(role, MemberType.CREATOR) || Objects.equals(role, MemberType.LEADER);
    };
  }
}
