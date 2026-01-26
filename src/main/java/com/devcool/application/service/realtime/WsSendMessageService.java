package com.devcool.application.service.realtime;

import com.devcool.domain.channel.exception.ChannelNotFoundException;
import com.devcool.domain.channel.model.Channel;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.member.exception.MemberNotFoundException;
import com.devcool.domain.member.model.Member;
import com.devcool.domain.member.port.out.MemberPort;
import com.devcool.domain.realtime.port.in.WsSendMessageUseCase;
import com.devcool.domain.realtime.port.in.command.SendMessageCommand;
import com.devcool.domain.realtime.port.out.ConnectionRegistryPort;
import com.devcool.domain.realtime.port.out.RealtimeEmitterPort;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class WsSendMessageService implements WsSendMessageUseCase {

  private static final Logger log = LoggerFactory.getLogger(WsSendMessageService.class);
  private final ConnectionRegistryPort connectionRegistryPort;
  private final RealtimeEmitterPort realtimeEmitterPort;

  private final ChannelPort channelPort;
  private final MemberPort memberPort;

  @Override
  @Transactional
  public void sendMessage(SendMessageCommand command) {
    // Validate channel status later
    Channel channel = channelPort.findById(command.channelId())
        .orElseThrow(() -> {
          log.warn("Channel id {} does not exist", command.channelId());
          throw new ChannelNotFoundException(command.channelId());
        });
    // Validate member permission later
    Member member = memberPort.findMemberOfChannelByUserId(command.channelId(), command.userId())
        .orElseThrow(() -> {
          log.info("User id {} does not exist in channel id {}", command.userId(), command.channelId());
          throw new MemberNotFoundException(command.userId());
        });

    Set<String> connections = connectionRegistryPort.getConnectionsByChannel(command.channelId());

    var payload = new OutboundWsEvent(
        "MESSAGE",
        command.channelId(),
        command.userId(),
        command.contentType().name(),
        command.content()
    );

    for (String connectionId : connections) {
      realtimeEmitterPort.sendMessageToConnection(connectionId, payload);
    }
  }

  public record OutboundWsEvent(
      String type,
      Integer channelId,
      Integer senderId,
      String contentType,
      String content
  ) {}
}
