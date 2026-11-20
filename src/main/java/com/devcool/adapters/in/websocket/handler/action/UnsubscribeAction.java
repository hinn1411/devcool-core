package com.devcool.adapters.in.websocket.handler.action;

import com.devcool.adapters.in.websocket.dto.ClientFrame;
import com.devcool.adapters.in.websocket.dto.FrameType;
import com.devcool.adapters.in.websocket.session.FrameWriter;
import com.devcool.domain.chat.port.in.WsUnsubscribeUseCase;
import com.devcool.domain.chat.port.in.command.UnsubscribeCommand;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
@RequiredArgsConstructor
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Spring injects shared singleton beans; they are not owned state")
public class UnsubscribeAction implements FrameAction {

  private final FrameWriter writer;
  private final WsUnsubscribeUseCase unsubscribeUseCase;

  @Override
  public FrameType type() {
    return FrameType.UNSUBSCRIBE;
  }

  @Override
  public void handle(WebSocketSession session, ClientFrame frame) throws IOException {
    if (Objects.isNull(frame.channelId())) {
      writer.error(session, frame.clientMsgId(), "Missing channelId");
      return;
    }
    unsubscribeUseCase.unsubscribe(new UnsubscribeCommand(session.getId(), frame.channelId()));
    writer.ack(session, frame.clientMsgId(), Map.of("description", "Unsubscribe successfully"));
  }
}
