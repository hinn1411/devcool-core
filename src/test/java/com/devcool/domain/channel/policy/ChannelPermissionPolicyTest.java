package com.devcool.domain.channel.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.devcool.domain.channel.model.enums.ChannelType;
import com.devcool.domain.member.model.enums.MemberType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The role table agreed in docs/journal/briefs/P1-T06.md, one row per cell. */
class ChannelPermissionPolicyTest {

  @ParameterizedTest(name = "{0} supports {1} → {2}")
  @CsvSource({
    "LOUNGE, UPDATE_CHANNEL, true",
    "LOUNGE, ADD_MEMBERS, true",
    "FORUM, UPDATE_CHANNEL, true",
    "FORUM, ADD_MEMBERS, true",
    "PRIVATE_CHAT, UPDATE_CHANNEL, true",
    "PRIVATE_CHAT, ADD_MEMBERS, false",
    // P3-T11: a DM keeps both people; only a forum has a leader to transfer.
    "LOUNGE, REMOVE_MEMBER, true",
    "LOUNGE, LEAVE_CHANNEL, true",
    "LOUNGE, TRANSFER_LEADER, false",
    "FORUM, REMOVE_MEMBER, true",
    "FORUM, LEAVE_CHANNEL, true",
    "FORUM, TRANSFER_LEADER, true",
    "PRIVATE_CHAT, REMOVE_MEMBER, false",
    "PRIVATE_CHAT, LEAVE_CHANNEL, false",
    "PRIVATE_CHAT, TRANSFER_LEADER, false",
  })
  void supports(ChannelType type, ChannelAction action, boolean expected) {
    assertThat(ChannelPermissionPolicy.supports(type, action)).isEqualTo(expected);
  }

  @ParameterizedTest(name = "{0}: {1} may {2} → {3}")
  @CsvSource({
    // A lounge has no leader: any member may manage it.
    "LOUNGE, MEMBER, UPDATE_CHANNEL, true",
    "LOUNGE, CREATOR, UPDATE_CHANNEL, true",
    "LOUNGE, LEADER, UPDATE_CHANNEL, true",
    "LOUNGE, MEMBER, ADD_MEMBERS, true",
    "LOUNGE, CREATOR, ADD_MEMBERS, true",
    "LOUNGE, LEADER, ADD_MEMBERS, true",
    // A forum is moderated: only the creator and the leader.
    "FORUM, MEMBER, UPDATE_CHANNEL, false",
    "FORUM, CREATOR, UPDATE_CHANNEL, true",
    "FORUM, LEADER, UPDATE_CHANNEL, true",
    "FORUM, MEMBER, ADD_MEMBERS, false",
    "FORUM, CREATOR, ADD_MEMBERS, true",
    "FORUM, LEADER, ADD_MEMBERS, true",
    // A private chat: either participant may update it; nobody may add a third person.
    "PRIVATE_CHAT, MEMBER, UPDATE_CHANNEL, true",
    "PRIVATE_CHAT, CREATOR, UPDATE_CHANNEL, true",
    "PRIVATE_CHAT, LEADER, UPDATE_CHANNEL, true",
    "PRIVATE_CHAT, MEMBER, ADD_MEMBERS, false",
    "PRIVATE_CHAT, CREATOR, ADD_MEMBERS, false",
    "PRIVATE_CHAT, LEADER, ADD_MEMBERS, false",
    // P3-T11 remove: a lounge's creator only; a forum's creator or leader.
    "LOUNGE, MEMBER, REMOVE_MEMBER, false",
    "LOUNGE, CREATOR, REMOVE_MEMBER, true",
    "LOUNGE, LEADER, REMOVE_MEMBER, false",
    "FORUM, MEMBER, REMOVE_MEMBER, false",
    "FORUM, CREATOR, REMOVE_MEMBER, true",
    "FORUM, LEADER, REMOVE_MEMBER, true",
    "PRIVATE_CHAT, CREATOR, REMOVE_MEMBER, false",
    // P3-T11 leave: every role (the service stops a forum leader who hasn't transferred).
    "LOUNGE, MEMBER, LEAVE_CHANNEL, true",
    "LOUNGE, CREATOR, LEAVE_CHANNEL, true",
    "FORUM, MEMBER, LEAVE_CHANNEL, true",
    "FORUM, CREATOR, LEAVE_CHANNEL, true",
    "FORUM, LEADER, LEAVE_CHANNEL, true",
    "PRIVATE_CHAT, MEMBER, LEAVE_CHANNEL, false",
    // P3-T11 transfer: a forum admin role (the service then checks leader_id).
    "FORUM, MEMBER, TRANSFER_LEADER, false",
    "FORUM, CREATOR, TRANSFER_LEADER, true",
    "FORUM, LEADER, TRANSFER_LEADER, true",
    "LOUNGE, CREATOR, TRANSFER_LEADER, false",
    "PRIVATE_CHAT, CREATOR, TRANSFER_LEADER, false",
  })
  void allows(ChannelType type, MemberType role, ChannelAction action, boolean expected) {
    assertThat(ChannelPermissionPolicy.allows(type, role, action)).isEqualTo(expected);
  }
}
