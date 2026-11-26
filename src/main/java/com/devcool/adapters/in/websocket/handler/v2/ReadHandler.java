package com.devcool.adapters.in.websocket.handler.v2;

import com.devcool.adapters.in.websocket.dto.v2.ClientEnvelope;
import com.devcool.adapters.in.websocket.dto.v2.ReadAckPayload;
import com.devcool.adapters.in.websocket.dto.v2.ReadPayload;
import com.devcool.adapters.in.websocket.dto.v2.ServerEnvelope;
import com.devcool.adapters.in.websocket.security.AuthHandshakeInterceptor;
import com.devcool.adapters.in.websocket.session.FrameWriter;
import com.devcool.domain.channel.port.in.MarkChannelReadUseCase;
import com.devcool.domain.channel.port.in.command.MarkChannelReadCommand;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/** v2 {@code READ} (P4-T04). */
@Component
@RequiredArgsConstructor
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Spring injects shared singleton beans; they are not owned state")
public class ReadHandler implements FrameHandler<ReadPayload> {

  private final FrameWriter writer;
  private final MarkChannelReadUseCase markChannelReadUseCase;

  @Override
  public String type() {
    return "READ";
  }

  @Override
  public Class<ReadPayload> payloadType() {
    return ReadPayload.class;
  }

  @Override
  public void handle(WebSocketSession session, ClientEnvelope frame, ReadPayload payload)
      throws IOException {
    long seq =
        markChannelReadUseCase.markRead(
            new MarkChannelReadCommand(
                session.getId(),
                ChannelIds.require(frame),
                AuthHandshakeInterceptor.userIdOf(session),
                Required.field(payload.seq(), "seq")));
    writer.send(session, ServerEnvelope.ack(frame.reqId(), new ReadAckPayload(seq)));
  }
}
