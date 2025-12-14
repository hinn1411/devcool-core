package com.devcool.domain.member.port.out;

import com.devcool.domain.member.model.Member;

public interface MemberPort {
  Integer save(Member member);

}
