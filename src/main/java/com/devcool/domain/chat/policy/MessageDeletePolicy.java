package com.devcool.domain.chat.policy;

import com.devcool.domain.channel.model.enums.ChannelType;
import com.devcool.domain.member.model.enums.MemberType;

/** Who may delete a message: the sender, or a CREATOR/LEADER of a group channel. */
public final class MessageDeletePolicy {

  private MessageDeletePolicy() {}

  public static boolean mayDelete(boolean isSender, ChannelType channelType, MemberType role) {
    throw new UnsupportedOperationException();
  }
}
