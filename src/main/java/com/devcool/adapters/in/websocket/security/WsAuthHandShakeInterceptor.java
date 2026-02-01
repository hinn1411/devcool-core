package com.devcool.adapters.in.websocket.security;

import com.devcool.domain.auth.port.out.LoadUserPort;
import com.devcool.domain.auth.port.out.TokenIssuerPort;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class WsAuthHandShakeInterceptor implements HandshakeInterceptor {
  public static final String ATTR_USER_ID = "userId";
  private static final Logger log = LoggerFactory.getLogger(WsAuthHandShakeInterceptor.class);
  private final TokenIssuerPort tokenIssuerPort;
  private final LoadUserPort loadUserPort;

  @Override
  public boolean beforeHandshake(ServerHttpRequest request,
                                 ServerHttpResponse response,
                                 WebSocketHandler wsHandler,
                                 Map<String, Object> attributes) {
    Integer userId = Optional.of(SecurityContextHolder.getContext().getAuthentication())
        .filter(Authentication::isAuthenticated)
        .map(Authentication::getPrincipal)
        .filter(Integer.class::isInstance)
        .map(Integer.class::cast)
        .orElse(null);

    if (Objects.isNull(userId)) {
      log.warn("Jwt is invalid, close handshake connection!");
      return false;
    }

    attributes.put(ATTR_USER_ID, userId);
    return true;
  }

  @Override
  public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
  }
}
