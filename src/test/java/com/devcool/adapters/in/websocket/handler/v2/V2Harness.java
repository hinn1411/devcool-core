package com.devcool.adapters.in.websocket.handler.v2;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import com.devcool.adapters.in.websocket.security.AuthHandshakeInterceptor;
import com.devcool.adapters.in.websocket.session.FrameWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.mockito.ArgumentCaptor;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/** One handler behind the real dispatcher; user 10 on connection conn-1. */
final class V2Harness {

  static final long USER = 10L;
  static final String CONNECTION = "conn-1";

  final ObjectMapper objectMapper =
      Jackson2ObjectMapperBuilder.json()
          .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .build();
  private final WebSocketSession session;
  private final FrameDispatcher dispatcher;

  V2Harness(WebSocketSession session, Function<FrameWriter, FrameHandler<?>> handler) {
    this.session = session;
    FrameWriter writer = new FrameWriter(objectMapper);
    dispatcher = new FrameDispatcher(objectMapper, writer, List.of(handler.apply(writer)));
    Map<String, Object> attributes = new HashMap<>();
    attributes.put(AuthHandshakeInterceptor.ATTR_USER_ID, USER);
    lenient().when(session.getId()).thenReturn(CONNECTION);
    lenient().when(session.getAttributes()).thenReturn(attributes);
  }

  void dispatch(String json) throws Exception {
    dispatcher.dispatch(session, objectMapper.readTree(json));
  }

  JsonNode onlyReply() throws Exception {
    ArgumentCaptor<TextMessage> sent = ArgumentCaptor.forClass(TextMessage.class);
    verify(session).sendMessage(sent.capture());
    return objectMapper.readTree(sent.getValue().getPayload());
  }
}
