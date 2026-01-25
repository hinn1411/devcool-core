package com.devcool.domain.member.exception;

import com.devcool.domain.common.DomainException;
import com.devcool.domain.common.ErrorCode;

import java.util.List;
import java.util.Map;

public class MemberAlreadyInChannelException extends DomainException {
  public MemberAlreadyInChannelException(List<Integer> memberIds) {
    super(ErrorCode.DUPLICATE_MEMBER, "Members already in channel", Map.of("memberIds", memberIds));
  }
}
