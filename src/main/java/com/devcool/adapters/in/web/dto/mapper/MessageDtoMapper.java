package com.devcool.adapters.in.web.dto.mapper;

import com.devcool.adapters.in.web.dto.response.GetMessagesResponse;
import com.devcool.adapters.in.web.dto.response.MessageItemResponse;
import com.devcool.domain.chat.model.MessageItem;
import com.devcool.domain.chat.model.MessageList;
import com.devcool.domain.chat.port.in.command.GetMessageCommand;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface MessageDtoMapper {

  GetMessageCommand toGetCommand(
      Integer userId, Integer channelId, Integer cursorId, Integer limit);

  GetMessagesResponse toGetResponse(MessageList messageList);

  MessageItemResponse toItemResponse(MessageItem item);
}
