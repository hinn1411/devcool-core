package com.devcool.adapters.in.web.dto.mapper;

import com.devcool.adapters.in.web.dto.response.GetMessagesResponse;
import com.devcool.adapters.in.web.dto.response.MessageItemResponse;
import com.devcool.domain.chat.model.MessageItem;
import com.devcool.domain.chat.model.MessageList;
import com.devcool.domain.chat.port.in.command.GetMessageCommand;
import org.springframework.stereotype.Component;

@Component
public class MessageDtoMapper {
  public GetMessageCommand toGetCommand(
      Integer userId, Integer channelId, Integer cursorId, Integer limit) {
    return new GetMessageCommand(userId, channelId, cursorId, limit);
  }

  public GetMessagesResponse toGetResponse(MessageList messageList) {
    return GetMessagesResponse.builder()
        .items(messageList.items().stream().map(this::toItemResponse).toList())
        .cursorId(messageList.cursorId())
        .hasMore(messageList.hasMore())
        .build();
  }

  private MessageItemResponse toItemResponse(MessageItem item) {
    return new MessageItemResponse(
        item.getId(),
        item.getContent(),
        item.getContentType().name(),
        item.getCreatedTime(),
        item.getUserId(),
        item.getSenderName(),
        item.getSenderAvatar());
  }
}
