package com.devcool.adapters.in.websocket.dto;

public record WsServerFrame (
    WsMessageType type, // "ACK", "ERROR", "INFO"
    String clientMsgId,
    Object data
) { }
