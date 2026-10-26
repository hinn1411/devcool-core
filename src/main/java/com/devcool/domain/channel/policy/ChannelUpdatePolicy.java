package com.devcool.domain.channel.policy;

import com.devcool.domain.channel.exception.InvalidChannelConfigException;
import com.devcool.domain.channel.model.enums.BoundaryType;
import com.devcool.domain.channel.model.enums.ChannelType;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Per-type channel config rules (brief P3-T00). Violations throw InvalidChannelConfigException. */
public final class ChannelUpdatePolicy {

  /** A lounge expiry must be more than this far ahead. */
  static final Duration MIN_LOUNGE_LIFETIME = Duration.ofDays(7);

  private ChannelUpdatePolicy() {}

  /** Partial update: a null type, boundary or expiry means unchanged. */
  public static void validateUpdate(
      ChannelType currentType,
      ChannelType requestedType,
      BoundaryType requestedBoundary,
      Instant expiredTime,
      Instant now) {
    if (Objects.nonNull(requestedType) && !Objects.equals(currentType, requestedType)) {
      throw new InvalidChannelConfigException("Channel type can't change");
    }
    if (Objects.equals(currentType, ChannelType.PRIVATE_CHAT)
        && Objects.equals(requestedBoundary, BoundaryType.PUBLIC)) {
      throw new InvalidChannelConfigException("Private chat must be PRIVATE");
    }
    if (Objects.isNull(expiredTime)) {
      return;
    }
    if (!Objects.equals(currentType, ChannelType.LOUNGE)) {
      throw new InvalidChannelConfigException("Only a lounge can have an expired time");
    }
    validateLoungeExpiry(expiredTime, now);
  }

  /** Null passes; otherwise it must be more than 7 days after now. */
  public static void validateLoungeExpiry(Instant expiredTime, Instant now) {
    if (Objects.nonNull(expiredTime) && !expiredTime.isAfter(now.plus(MIN_LOUNGE_LIFETIME))) {
      throw new InvalidChannelConfigException("Lounge expired time must be more than 7 days ahead");
    }
  }
}
