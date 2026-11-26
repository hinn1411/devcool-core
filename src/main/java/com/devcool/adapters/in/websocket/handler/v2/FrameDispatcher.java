package com.devcool.adapters.in.websocket.handler.v2;

import com.devcool.adapters.in.websocket.dto.v2.ClientEnvelope;
import com.devcool.adapters.in.websocket.dto.v2.NackCode;
import com.devcool.adapters.in.websocket.dto.v2.ServerEnvelope;
import com.devcool.adapters.in.websocket.session.FrameWriter;
import com.devcool.domain.common.DomainException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/**
 * Routes a v2 frame to its {@link FrameHandler}. Every failure becomes one NACK; none closes the
 * socket (ADR-0005 rules 1, 2, 4).
 */
@Component
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Spring injects shared singleton beans; they are not owned state")
public class FrameDispatcher {

  private static final Logger log = LoggerFactory.getLogger(FrameDispatcher.class);

  private final ObjectMapper objectMapper;
  private final FrameWriter writer;
  private final Map<String, FrameHandler<?>> handlers = new HashMap<>();

  public FrameDispatcher(
      ObjectMapper objectMapper, FrameWriter writer, List<FrameHandler<?>> handlers) {
    this.objectMapper = objectMapper;
    this.writer = writer;
    for (FrameHandler<?> handler : handlers) {
      if (Objects.nonNull(this.handlers.put(handler.type(), handler))) {
        throw new IllegalStateException("Two handlers for " + handler.type());
      }
    }
  }

  public void dispatch(WebSocketSession session, JsonNode frame) throws IOException {
    String reqId = textOrNull(frame.get("reqId"));
    try {
      route(session, frame, reqId);
    } catch (InvalidFrameException ex) {
      nack(session, reqId, NackCode.INVALID, ex.getMessage());
    } catch (DomainException ex) {
      nack(session, reqId, NackCodeMapper.toNackCode(ex.getErrorCode()), ex.getErrorCode().code());
    } catch (RuntimeException ex) {
      // MDC already carries userId and connectionId.
      log.error("Unhandled {} frame failure", textOrNull(frame.get("type")), ex);
      nack(session, reqId, NackCode.INTERNAL, "Internal error");
    }
  }

  private void route(WebSocketSession session, JsonNode tree, String reqId) throws IOException {
    ClientEnvelope frame = read(tree, ClientEnvelope.class, "Invalid envelope");
    if (Objects.isNull(textOrNull(tree.get("type")))) {
      throw new InvalidFrameException("Missing type");
    }
    FrameHandler<?> handler = handlers.get(frame.type());
    if (Objects.isNull(handler)) {
      nack(session, reqId, NackCode.UNSUPPORTED, "Unsupported type");
      return;
    }
    if (Objects.isNull(reqId)) {
      throw new InvalidFrameException("Missing reqId");
    }
    handle(session, frame, handler);
  }

  private <P> void handle(WebSocketSession session, ClientEnvelope frame, FrameHandler<P> handler)
      throws IOException {
    JsonNode payload =
        Objects.isNull(frame.payload()) || frame.payload().isNull()
            ? objectMapper.createObjectNode()
            : frame.payload();
    handler.handle(session, frame, read(payload, handler.payloadType(), "Invalid payload"));
  }

  private <T> T read(JsonNode node, Class<T> type, String problem) {
    try {
      return objectMapper.treeToValue(node, type);
    } catch (JsonProcessingException | IllegalArgumentException ex) {
      throw new InvalidFrameException(problem);
    }
  }

  private void nack(WebSocketSession session, String reqId, NackCode code, String message)
      throws IOException {
    writer.send(session, ServerEnvelope.nack(reqId, code, message));
  }

  /** A non-blank string, else null: a blank reqId correlates nothing. */
  private static String textOrNull(JsonNode node) {
    return Objects.nonNull(node) && node.isTextual() && !node.asText().isBlank()
        ? node.asText()
        : null;
  }
}
