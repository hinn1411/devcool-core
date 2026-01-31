package com.devcool.domain.chat.model.enums;

public enum ContentType {
  TEXT(0),
  MARKDOWN(1),
  IMAGE(2),
  VIDEO(3);

  private final int code;

  ContentType(int code) {
    this.code = code;
  }

  public int getCode() {
    return code;
  }

  public static ContentType fromCode(Integer code) {
    if (code == null) return null;
    for (ContentType c : ContentType.values()) {
      if (c.code == code) return c;
    }
    return null;
  }
}
