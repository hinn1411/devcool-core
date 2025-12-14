package com.devcool.domain.member.model;

import com.devcool.domain.model.enums.MemberType;
import com.devcool.domain.user.model.User;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class Member {
  private Integer id;
  private User user;
  private MemberType role;
  private Instant joinedTime;
}
