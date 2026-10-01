package com.devcool.domain.user.port.in.command;

public record ChangePasswordCommand(
    Integer userId, String currentPassword, String newPassword, String confirmedPassword) {

  // The generated toString would print the passwords; keep them out of logs.
  @Override
  public String toString() {
    return "ChangePasswordCommand[userId=" + userId + "]";
  }
}
