package com.devcool.adapters.in.websocket.config;

import com.devcool.adapters.in.websocket.handler.ChatHandler;
import com.devcool.adapters.in.websocket.logging.MdcHandlerDecorator;
import com.devcool.adapters.in.websocket.security.AuthHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@ConditionalOnWebApplication
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

  private final ChatHandler handler;
  private final AuthHandshakeInterceptor authInterceptor;

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry
        .addHandler(new MdcHandlerDecorator(handler), "/ws")
        .addInterceptors(authInterceptor)
        .setAllowedOrigins("*");
  }
}
