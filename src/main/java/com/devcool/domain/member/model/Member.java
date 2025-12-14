package com.devcool.domain.member.model;

import com.devcool.domain.model.enums.MemberType;
import com.devcool.domain.user.model.User;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
public class Member {
  private Integer id;
  private User user;
  private MemberType role;
  private Instant joinedTime;
}
