package com.devcool.domain.channel.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devcool.domain.channel.exception.InvalidChannelConfigException;
import org.junit.jupiter.api.Test;

class DirectChatPairTest {

  @Test
  void bothOrdersGiveTheSamePair_lowIdFirst() {
    assertThat(DirectChatPair.of(7L, 3L))
        .isEqualTo(DirectChatPair.of(3L, 7L))
        .isEqualTo(new DirectChatPair(3L, 7L));
  }

  @Test
  void rejectsAPairWithYourself() {
    assertThatThrownBy(() -> DirectChatPair.of(5L, 5L))
        .isInstanceOf(InvalidChannelConfigException.class);
  }
}
