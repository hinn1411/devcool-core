package com.devcool.domain.channel.model.enums;

public enum ChannelType {
  LOUNGE(0),
  FORUM(1),
  PRIVATE_CHAT(2);

  private final int code;

  ChannelType(int code) {
    this.code = code;
  }

  public int getCode() {
    return code;
  }

  public static ChannelType fromCode(Integer code) {
    if (code == null) return null;
    for (ChannelType b : ChannelType.values()) {
      if (b.code == code) return b;
    }
    return null;
  }
}
