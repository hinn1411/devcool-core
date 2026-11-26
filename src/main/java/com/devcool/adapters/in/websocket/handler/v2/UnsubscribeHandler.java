package com.devcool.adapters.in.websocket.handler.v2;

import com.devcool.adapters.in.websocket.dto.v2.ClientEnvelope;
import com.devcool.adapters.in.websocket.dto.v2.EmptyPayload;
import com.devcool.adapters.in.websocket.dto.v2.ServerEnvelope;
import com.devcool.adapters.in.websocket.session.FrameWriter;
import com.devcool.domain.chat.port.in.ChannelSubscriptionUseCase;
import com.devcool.domain.chat.port.in.command.UnsubscribeCommand;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
@RequiredArgsConstructor
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Spring injects shared singleton beans; they are not owned state")
public class UnsubscribeHandler implements FrameHandler<EmptyPayload> {

  private final FrameWriter writer;
  private final ChannelSubscriptionUseCase subscriptions;

  @Override
  public String type() {
    return "UNSUBSCRIBE";
  }

  @Override
  public Class<EmptyPayload> payloadType() {
    return EmptyPayload.class;
  }

  @Override
  public void handle(WebSocketSession session, ClientEnvelope frame, EmptyPayload payload)
      throws IOException {
    subscriptions.unsubscribe(new UnsubscribeCommand(session.getId(), ChannelIds.require(frame)));
    writer.send(session, ServerEnvelope.ack(frame.reqId(), null));
  }
}
