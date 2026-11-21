package com.devcool.adapters.in.websocket.dto;

public record ServerFrame(
    FrameType type, // "ACK", "ERROR", "INFO"
    String clientMsgId,
    Object data) {}
