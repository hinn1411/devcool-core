package com.devcool.domain.member.exception;

import com.devcool.domain.common.DomainException;
import com.devcool.domain.common.ErrorCode;

import java.util.Map;

public class MemberNotFoundException extends DomainException {

  public MemberNotFoundException(Integer memberId) {
    super(ErrorCode.MEMBER_NOT_FOUND, "Member does not exist in channel" , Map.of("memberId", memberId));
  }
}
