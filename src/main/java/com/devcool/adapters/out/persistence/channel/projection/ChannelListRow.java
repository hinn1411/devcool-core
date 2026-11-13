package com.devcool.adapters.out.persistence.channel.projection;

import com.devcool.domain.channel.model.enums.BoundaryType;
import com.devcool.domain.channel.model.enums.ChannelType;
import com.devcool.domain.chat.model.enums.ContentType;
import java.time.Instant;

/** A channel joined to its newest message; the {@code last*} columns are null with no messages. */
public record ChannelListRow(
    Long id,
    String name,
    ChannelType channelType,
    BoundaryType boundaryType,
    Long unreadCount,
    Long lastMessageId,
    Long lastMessageSeq,
    Long lastSenderId,
    String lastSenderName,
    ContentType lastContentType,
    String lastContent,
    Instant lastDeletedTime) {}
