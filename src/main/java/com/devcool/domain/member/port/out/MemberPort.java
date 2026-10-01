package com.devcool.domain.member.port.out;

import com.devcool.domain.member.model.Member;
import com.devcool.domain.member.model.enums.MemberType;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface MemberPort {
  List<Member> findMembersOfChannelByUserIds(Integer channelId, Set<Integer> userIds);

  boolean existMemberOfChannelByUserId(Integer channelId, Integer userId);

  /** The user's role in the channel, or empty when the user is not a member. */
  Optional<MemberType> findRoleOfMember(Integer channelId, Integer userId);

  boolean addMembers(Integer channelId, Set<Integer> memberIds);
}
