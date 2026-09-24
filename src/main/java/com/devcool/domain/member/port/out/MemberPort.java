package com.devcool.domain.member.port.out;

import com.devcool.domain.member.model.Member;
import java.util.List;
import java.util.Set;

public interface MemberPort {
  List<Member> findMembersOfChannelByUserIds(Integer channelId, Set<Integer> userIds);

  boolean existMemberOfChannelByUserId(Integer channelId, Integer userId);

  boolean addMembers(Integer channelId, Set<Integer> memberIds);
}
