package com.devcool.application.service.chat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.devcool.domain.chat.model.SavedMessage;
import com.devcool.domain.chat.model.enums.ContentType;
import com.devcool.domain.chat.port.in.SaveMessageUseCase;
import com.devcool.domain.chat.port.in.command.SendMessageCommand;
import com.devcool.domain.chat.port.out.ConnectionRegistryPort;
import com.devcool.domain.chat.port.out.RealtimeEmitterPort;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WsSendMessageServiceTest {

  @Mock private ConnectionRegistryPort connectionRegistryPort;
  @Mock private RealtimeEmitterPort realtimeEmitterPort;
  @Mock private SaveMessageUseCase saveMessageUseCase;
  @InjectMocks private WsSendMessageService service;

  @Test
  void retriedClientMsgId_isNotBroadcastAgain() {
    when(saveMessageUseCase.save(any()))
        .thenReturn(new SavedMessage(100L, 42L, Instant.parse("2026-11-01T10:00:00Z"), true));

    service.sendMessage(
        new SendMessageCommand("conn-1", 10L, 1L, ContentType.TEXT, "hello", UUID.randomUUID()));

    // The original send already reached the channel; a retry must not show the message twice.
    verifyNoInteractions(connectionRegistryPort, realtimeEmitterPort);
  }
}
