package com.devcool.adapters.in.websocket.dto;

public enum FrameType {
  SUBSCRIBE,
  UNSUBSCRIBE,
  SEND_MESSAGE,
  ACK,
  ERROR,
  PING
}
