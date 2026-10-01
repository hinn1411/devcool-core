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
  })
  void allows(ChannelType type, MemberType role, ChannelAction action, boolean expected) {
    assertThat(ChannelPermissionPolicy.allows(type, role, action)).isEqualTo(expected);
  }
}
