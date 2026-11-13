package com.devcool.application.service.channel;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devcool.domain.channel.exception.ChannelNotFoundException;
import com.devcool.domain.channel.port.in.command.MarkChannelReadCommand;
import com.devcool.domain.member.port.out.MemberPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** P3-T10 loop 1: one guarded UPDATE; no matched row = not your channel = 404. */
@ExtendWith(MockitoExtension.class)
class ReadStateServiceTest {

  private static final long CHANNEL = 1L, ALICE = 10L, SEQ = 42L;

  @Mock private MemberPort memberPort;

  private ReadStateService service;

  @BeforeEach
  void setUp() {
    service = new ReadStateService(memberPort);
  }

  @Test
  void member_advancesTheirOwnMarker() {
    when(memberPort.advanceLastReadSeq(CHANNEL, ALICE, SEQ)).thenReturn(true);

    service.markRead(new MarkChannelReadCommand(CHANNEL, ALICE, SEQ));

    verify(memberPort).advanceLastReadSeq(CHANNEL, ALICE, SEQ);
  }

  @Test
  void nonMember_is404() {
    when(memberPort.advanceLastReadSeq(CHANNEL, ALICE, SEQ)).thenReturn(false);

    assertThatThrownBy(() -> service.markRead(new MarkChannelReadCommand(CHANNEL, ALICE, SEQ)))
        .isInstanceOf(ChannelNotFoundException.class);
  }
}
