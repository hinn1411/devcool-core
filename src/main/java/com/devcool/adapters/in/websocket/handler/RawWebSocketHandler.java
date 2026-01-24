package com.devcool.adapters.in.websocket.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
@RequiredArgsConstructor
public class RawWebSocketHandler extends TextWebSocketHandler {

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {

  }

  @Override
  public void handleTextMessage(WebSocketSession session, TextMessage message) {

  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
  }

}
