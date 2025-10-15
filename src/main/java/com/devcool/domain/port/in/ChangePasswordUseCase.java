package com.devcool.domain.port.in;

public interface ChangePasswordUseCase {
    boolean change(Integer userId, String currentRawPassword, String newRawPassword);
}
