package com.devcool.domain.member.port.out;

import com.devcool.domain.member.model.Member;

import java.util.List;
import java.util.Set;

public interface MemberPort {
  List<Member> findMembersOfChannelByUserIds(Integer channelId, Set<Integer> ids);

  boolean addMembers(Integer channelId, Set<Integer> memberIds);
}
