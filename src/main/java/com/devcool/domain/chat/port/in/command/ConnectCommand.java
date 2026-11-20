package com.devcool.domain.chat.port.in.command;

/**
 * {@code userId} is the authenticated handshake's; {@code connectionId} the server's session id.
 */
public record ConnectCommand(String connectionId, Long userId) {}
