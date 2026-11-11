package com.devcool.adapters.in.web.controller;

import com.devcool.adapters.in.web.dto.mapper.MessageDtoMapper;
import com.devcool.adapters.in.web.dto.request.EditMessageRequest;
import com.devcool.adapters.in.web.dto.response.EditMessageResponse;
import com.devcool.adapters.in.web.dto.response.GetMessagesResponse;
import com.devcool.adapters.in.web.dto.wrapper.ApiSuccessResponse;
import com.devcool.adapters.in.web.util.ApiResponseFactory;
import com.devcool.domain.chat.model.MessageList;
import com.devcool.domain.chat.port.in.DeleteMessageUseCase;
import com.devcool.domain.chat.port.in.EditMessageUseCase;
import com.devcool.domain.chat.port.in.GetMessageQuery;
import com.devcool.domain.chat.port.in.command.DeleteMessageCommand;
import com.devcool.domain.chat.port.in.command.EditMessageCommand;
import com.devcool.domain.chat.port.in.command.GetMessageCommand;
import com.devcool.domain.common.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MessageController {
  private final GetMessageQuery messageQuerier;
  private final EditMessageUseCase messageEditor;
  private final DeleteMessageUseCase messageDeleter;
  private final MessageDtoMapper mapper;

  @GetMapping("/channels/{channelId}/messages")
  public ResponseEntity<ApiSuccessResponse<GetMessagesResponse>> getMessages(
      @PathVariable Long channelId,
      @Parameter(deprecated = true, description = "Use beforeSeq. Message id; older first.")
          @RequestParam(required = false)
          Long cursorId,
      @Parameter(
              description =
                  "Older than this seq, newest first. Next page: the last item's seq (exclusive).")
          @RequestParam(required = false)
          @Min(1)
          Long beforeSeq,
      @Parameter(
              description =
                  "Newer than this seq, oldest first. Next page: the last item's seq (exclusive).")
          @RequestParam(required = false)
          @Min(0)
          Long afterSeq,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer limit,
      Authentication auth) {
    Long userId = Long.valueOf(auth.getName());
    GetMessageCommand command =
        mapper.toGetCommand(userId, channelId, cursorId, beforeSeq, afterSeq, limit);
    MessageList messageList = messageQuerier.getMessages(command);
    GetMessagesResponse response = mapper.toGetResponse(messageList);
    return ResponseEntity.ok(
        ApiResponseFactory.success(
            HttpStatus.OK, ErrorCode.OK.code(), "Getting messages successfully", response));
  }

  @Operation(
      summary = "Edit a message",
      description =
          "Sender only, TEXT/MARKDOWN, within 24 h of sending. Send the version you last saw:"
              + " a stale one gets 409 (reload and retry). Unchanged content is a no-op.")
  @PatchMapping("/messages/{messageId}")
  public ResponseEntity<ApiSuccessResponse<EditMessageResponse>> editMessage(
      @PathVariable Long messageId,
      @Valid @RequestBody EditMessageRequest request,
      Authentication auth) {
    Long userId = Long.valueOf(auth.getName());
    EditMessageCommand command = mapper.toEditCommand(userId, messageId, request);
    EditMessageResponse response = mapper.toEditResponse(messageEditor.edit(command));
    return ResponseEntity.ok(
        ApiResponseFactory.success(HttpStatus.OK, ErrorCode.OK.code(), "Message edited", response));
  }

  @Operation(
      summary = "Delete a message",
      description =
          "Soft delete by the sender, a LOUNGE/FORUM CREATOR or a FORUM LEADER. History keeps it"
              + " as a tombstone (content null, deletedTime set). Repeating it is a no-op.")
  @DeleteMapping("/messages/{messageId}")
  public ResponseEntity<Void> deleteMessage(@PathVariable Long messageId, Authentication auth) {
    messageDeleter.delete(new DeleteMessageCommand(Long.valueOf(auth.getName()), messageId));
    return ResponseEntity.noContent().build();
  }
}
