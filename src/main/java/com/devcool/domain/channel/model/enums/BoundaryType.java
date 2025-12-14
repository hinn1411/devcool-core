package com.devcool.domain.channel.model.enums;

public enum BoundaryType {
  PUBLIC(0),
  PRIVATE(1);

  private final int code;

  BoundaryType(int code) {
    this.code = code;
  }

  public int getCode() {
    return code;
  }

  public static BoundaryType fromCode(Integer code) {
    if (code == null) return null;
    for (BoundaryType b : BoundaryType.values()) {
      if (b.code == code) return b;
    }
    return null;
  }
}
