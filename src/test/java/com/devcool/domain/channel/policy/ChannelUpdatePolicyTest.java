package com.devcool.domain.channel.policy;

import static org.assertj.core.api.Assertions.*;

import com.devcool.domain.channel.exception.InvalidChannelConfigException;
import com.devcool.domain.channel.model.enums.BoundaryType;
import com.devcool.domain.channel.model.enums.ChannelType;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Rules from docs/journal/briefs/P3-T00.md. */
class ChannelUpdatePolicyTest {

  private static final Instant NOW = Instant.parse("2026-10-26T10:00:00Z");
  private static final Duration SEVEN_DAYS = ChannelUpdatePolicy.MIN_LOUNGE_LIFETIME;

  @Nested
  class ValidateUpdate {

    @ParameterizedTest(name = "{0} → {1} is rejected")
    @CsvSource({
      "PRIVATE_CHAT, LOUNGE",
      "PRIVATE_CHAT, FORUM",
      "LOUNGE, FORUM",
      "LOUNGE, PRIVATE_CHAT",
      "FORUM, LOUNGE",
      "FORUM, PRIVATE_CHAT",
    })
    void typeChange_isRejected(ChannelType current, ChannelType requested) {
      assertThatThrownBy(
              () -> ChannelUpdatePolicy.validateUpdate(current, requested, null, null, NOW))
          .isInstanceOf(InvalidChannelConfigException.class);
    }

    @ParameterizedTest(name = "{0} → {0} passes")
    @EnumSource(ChannelType.class)
    void sameType_passes(ChannelType type) {
      assertThatNoException()
          .isThrownBy(() -> ChannelUpdatePolicy.validateUpdate(type, type, null, null, NOW));
    }

    @ParameterizedTest(name = "{0}: no type sent passes")
    @EnumSource(ChannelType.class)
    void noTypeSent_passes(ChannelType type) {
      assertThatNoException()
          .isThrownBy(() -> ChannelUpdatePolicy.validateUpdate(type, null, null, null, NOW));
    }

    @ParameterizedTest(name = "{0} can't get an expiry")
    @EnumSource(
        value = ChannelType.class,
        names = {"PRIVATE_CHAT", "FORUM"})
    void expiryOnPrivateChatOrForum_isRejected(ChannelType type) {
      // Far ahead, so only the type rule can reject it
      Instant farAhead = NOW.plus(Duration.ofDays(30));
      assertThatThrownBy(() -> ChannelUpdatePolicy.validateUpdate(type, type, null, farAhead, NOW))
          .isInstanceOf(InvalidChannelConfigException.class);
    }

    @ParameterizedTest(name = "{0}: no expiry sent passes")
    @EnumSource(ChannelType.class)
    void noExpirySent_passes(ChannelType type) {
      assertThatNoException()
          .isThrownBy(() -> ChannelUpdatePolicy.validateUpdate(type, type, null, null, NOW));
    }

    @Test
    void loungeExpiryTooSoon_isRejectedThroughUpdate() {
      assertThatThrownBy(
              () ->
                  ChannelUpdatePolicy.validateUpdate(
                      ChannelType.LOUNGE, null, null, NOW.plus(SEVEN_DAYS), NOW))
          .isInstanceOf(InvalidChannelConfigException.class);
    }

    @Test
    void loungeExpiryFarEnough_passesThroughUpdate() {
      Instant expiry = NOW.plus(SEVEN_DAYS).plusSeconds(1);
      assertThatNoException()
          .isThrownBy(
              () ->
                  ChannelUpdatePolicy.validateUpdate(ChannelType.LOUNGE, null, null, expiry, NOW));
    }
  }

  @Nested
  class Boundary {

    @Test
    void privateChatToPublic_isRejected() {
      assertThatThrownBy(
              () ->
                  ChannelUpdatePolicy.validateUpdate(
                      ChannelType.PRIVATE_CHAT, null, BoundaryType.PUBLIC, null, NOW))
          .isInstanceOf(InvalidChannelConfigException.class);
    }

    @ParameterizedTest(name = "{0} → {1} passes")
    @CsvSource({
      "PRIVATE_CHAT, PRIVATE",
      "LOUNGE, PUBLIC",
      "LOUNGE, PRIVATE",
      "FORUM, PUBLIC",
      "FORUM, PRIVATE",
    })
    void boundaryChange_passes(ChannelType type, BoundaryType boundary) {
      assertThatNoException()
          .isThrownBy(() -> ChannelUpdatePolicy.validateUpdate(type, null, boundary, null, NOW));
    }
  }

  @Nested
  class ValidateLoungeExpiry {

    @Test
    void noExpiry_passes() {
      assertThatNoException().isThrownBy(() -> ChannelUpdatePolicy.validateLoungeExpiry(null, NOW));
    }

    @Test
    void expiryExactlySevenDaysAhead_isRejected() {
      assertThatThrownBy(() -> ChannelUpdatePolicy.validateLoungeExpiry(NOW.plus(SEVEN_DAYS), NOW))
          .isInstanceOf(InvalidChannelConfigException.class);
    }

    @Test
    void expiryJustOverSevenDaysAhead_passes() {
      assertThatNoException()
          .isThrownBy(
              () ->
                  ChannelUpdatePolicy.validateLoungeExpiry(
                      NOW.plus(SEVEN_DAYS).plusSeconds(1), NOW));
    }

    @ParameterizedTest(name = "now + {0}d is rejected")
    @ValueSource(longs = {-1, 0, 1, 6})
    void expiryWithinSevenDays_isRejected(long daysAhead) {
      assertThatThrownBy(
              () ->
                  ChannelUpdatePolicy.validateLoungeExpiry(
                      NOW.plus(daysAhead, ChronoUnit.DAYS), NOW))
          .isInstanceOf(InvalidChannelConfigException.class);
    }
  }
}
