package com.devcool.application.service.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.devcool.application.service.chat.ChannelEventPublisher;
import com.devcool.domain.channel.exception.ChannelNotFoundException;
import com.devcool.domain.channel.port.in.command.MarkChannelReadCommand;
import com.devcool.domain.chat.model.ReadReceiptEvent;
import com.devcool.domain.member.model.ReadMarker;
import com.devcool.domain.member.port.out.MemberPort;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** One guarded UPDATE (non-member = 404); a receipt only when the marker moves. */
@ExtendWith(MockitoExtension.class)
class ReadStateServiceTest {

  private static final long CHANNEL = 1L, ALICE = 10L, SEQ = 42L;

  @Mock private MemberPort memberPort;
  @Mock private ChannelEventPublisher eventPublisher;

  private ReadStateService service;

  @BeforeEach
  void setUp() {
    service = new ReadStateService(memberPort, eventPublisher);
  }

  @Test
  void member_advancesTheirOwnMarker() {
    when(memberPort.advanceLastReadSeq(CHANNEL, ALICE, SEQ))
        .thenReturn(Optional.of(new ReadMarker(5, SEQ)));

    service.markRead(new MarkChannelReadCommand(CHANNEL, ALICE, SEQ));

    verify(memberPort).advanceLastReadSeq(CHANNEL, ALICE, SEQ);
  }

  @Test
  void nonMember_is404() {
    when(memberPort.advanceLastReadSeq(CHANNEL, ALICE, SEQ)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.markRead(new MarkChannelReadCommand(CHANNEL, ALICE, SEQ)))
        .isInstanceOf(ChannelNotFoundException.class);
    verifyNoInteractions(eventPublisher);
  }

  @Test
  void advance_returnsTheClampedMarker_andPublishesAReceipt_skippingTheActorsSocket() {
    // Asked for 42, the channel's last seq is 30.
    when(memberPort.advanceLastReadSeq(CHANNEL, ALICE, SEQ))
        .thenReturn(Optional.of(new ReadMarker(5, 30)));

    long marker = service.markRead(new MarkChannelReadCommand("conn-a", CHANNEL, ALICE, SEQ));

    assertThat(marker).isEqualTo(30);
    verify(eventPublisher).publishAfterCommit(new ReadReceiptEvent(CHANNEL, ALICE, 30), "conn-a");
  }

  @Test
  void olderOrSameSeq_returnsTheCurrentMarker_andPublishesNothing() {
    when(memberPort.advanceLastReadSeq(CHANNEL, ALICE, 3))
        .thenReturn(Optional.of(new ReadMarker(SEQ, SEQ)));

    long marker = service.markRead(new MarkChannelReadCommand(CHANNEL, ALICE, 3));

    assertThat(marker).isEqualTo(SEQ);
    verifyNoInteractions(eventPublisher);
  }
}
