package com.devcool.adapters.in.websocket;

import com.devcool.adapters.in.websocket.handler.RawWebSocketHandler;
import com.devcool.adapters.in.websocket.security.WsAuthHandShakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

  private final RawWebSocketHandler handler;
  private final WsAuthHandShakeInterceptor authInterceptor;
  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(handler, "/ws")
        .addInterceptors(authInterceptor)
        .setAllowedOrigins("*");
  }
}
