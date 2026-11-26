package com.devcool.adapters.in.websocket.session;

import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * A raw session that behaves like Tomcat's: two overlapping {@code sendMessage} calls fail with
 * {@code IllegalStateException}. {@link #block()} makes the next send hang until {@link
 * #unblock()}, standing in for a client whose TCP buffer is full.
 */
final class FakeWsSession implements WebSocketSession {

  private final String id;
  private final Queue<String> received = new ConcurrentLinkedQueue<>();
  private final AtomicInteger inFlight = new AtomicInteger();
  private final AtomicReference<CloseStatus> closeStatus = new AtomicReference<>();
  private final Map<String, Object> attributes = new ConcurrentHashMap<>();
  private volatile CountDownLatch gate = new CountDownLatch(0);
  private final CountDownLatch sendStarted = new CountDownLatch(1);

  FakeWsSession(String id) {
    this.id = id;
  }

  void block() {
    gate = new CountDownLatch(1);
  }

  void unblock() {
    gate.countDown();
  }

  /** Waits until a send is inside the socket write. */
  void awaitSendStarted() throws InterruptedException {
    if (!sendStarted.await(5, TimeUnit.SECONDS)) {
      throw new AssertionError("no send started on " + id);
    }
  }

  Queue<String> received() {
    return received;
  }

  CloseStatus closeStatus() {
    return closeStatus.get();
  }

  @Override
  public void sendMessage(WebSocketMessage<?> message) {
    if (inFlight.incrementAndGet() > 1) {
      inFlight.decrementAndGet();
      throw new IllegalStateException("The remote endpoint was in state [TEXT_PARTIAL_WRITING]");
    }
    try {
      sendStarted.countDown();
      gate.await();
      received.add(message.getPayload().toString());
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    } finally {
      inFlight.decrementAndGet();
    }
  }

  @Override
  public boolean isOpen() {
    return closeStatus.get() == null;
  }

  @Override
  public void close() {
    close(CloseStatus.NORMAL);
  }

  @Override
  public void close(CloseStatus status) {
    closeStatus.compareAndSet(null, status);
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public Map<String, Object> getAttributes() {
    return attributes;
  }

  @Override
  public URI getUri() {
    return null;
  }

  @Override
  public HttpHeaders getHandshakeHeaders() {
    return new HttpHeaders();
  }

  @Override
  public Principal getPrincipal() {
    return null;
  }

  @Override
  public InetSocketAddress getLocalAddress() {
    return null;
  }

  @Override
  public InetSocketAddress getRemoteAddress() {
    return null;
  }

  @Override
  public String getAcceptedProtocol() {
    return null;
  }

  @Override
  public void setTextMessageSizeLimit(int messageSizeLimit) {}

  @Override
  public int getTextMessageSizeLimit() {
    return 0;
  }

  @Override
  public void setBinaryMessageSizeLimit(int messageSizeLimit) {}

  @Override
  public int getBinaryMessageSizeLimit() {
    return 0;
  }

  @Override
  public List<WebSocketExtension> getExtensions() {
    return List.of();
  }
}
