package com.devcool.domain.user.port.in;

public interface ChangePasswordUseCase {
  boolean change(Integer userId, String currentRawPassword, String newRawPassword);
}
