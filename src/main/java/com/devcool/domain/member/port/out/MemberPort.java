package com.devcool.domain.member.port.out;

import com.devcool.domain.member.model.Member;
import com.devcool.domain.member.model.enums.MemberType;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface MemberPort {
  List<Member> findMembersOfChannelByUserIds(Long channelId, Set<Long> userIds);

  boolean existMemberOfChannelByUserId(Long channelId, Long userId);

  /** The user's role in the channel, or empty when the user is not a member. */
  Optional<MemberType> findRoleOfMember(Long channelId, Long userId);

  boolean addMembers(Long channelId, Set<Long> memberIds);
}
