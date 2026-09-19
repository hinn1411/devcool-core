package com.devcool.adapters.in.web.controller;

import com.devcool.adapters.in.web.dto.mapper.MessageDtoMapper;
import com.devcool.adapters.in.web.dto.response.GetMessagesResponse;
import com.devcool.adapters.in.web.dto.wrapper.ApiSuccessResponse;
import com.devcool.adapters.in.web.util.ApiResponseFactory;
import com.devcool.domain.chat.model.MessageList;
import com.devcool.domain.chat.port.in.GetMessageQuery;
import com.devcool.domain.chat.port.in.command.GetMessageCommand;
import com.devcool.domain.common.ErrorCode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MessageController {
  private final GetMessageQuery messageQuerier;
  private final MessageDtoMapper mapper;

  @GetMapping("/channels/{channelId}/messages")
  public ResponseEntity<ApiSuccessResponse<GetMessagesResponse>> getMessages(
      @PathVariable Integer channelId,
      @RequestParam(required = false) Integer cursorId,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer limit,
      Authentication auth) {
    Integer userId = Integer.valueOf(auth.getName());
    GetMessageCommand command = mapper.toGetCommand(userId, channelId, cursorId, limit);
    MessageList messageList = messageQuerier.getMessages(command);
    GetMessagesResponse response = mapper.toGetResponse(messageList);
    return ResponseEntity.ok(
        ApiResponseFactory.success(
            HttpStatus.OK, ErrorCode.OK.code(), "Getting messages successfully", response));
  }
}
