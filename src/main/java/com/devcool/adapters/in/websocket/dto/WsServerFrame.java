package com.devcool.adapters.in.websocket.dto;

public record WsServerFrame (
    String type, // "ACK", "ERROR", "INFO"
    String clientMsgId,
    Object data
) { }
