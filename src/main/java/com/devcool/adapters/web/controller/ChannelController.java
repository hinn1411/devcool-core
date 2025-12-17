package com.devcool.adapters.web.controller;

import com.devcool.adapters.web.dto.mapper.ChannelDtoMapper;
import com.devcool.adapters.web.dto.request.CreateChannelRequest;
import com.devcool.adapters.web.dto.request.UpdateChannelRequest;
import com.devcool.adapters.web.dto.response.CreateChannelResponse;
import com.devcool.adapters.web.dto.response.UpdateChannelResponse;
import com.devcool.adapters.web.dto.wrapper.ApiSuccessResponse;
import com.devcool.adapters.web.util.ApiResponseFactory;
import com.devcool.domain.channel.port.in.CreateChannelUseCase;
import com.devcool.domain.channel.port.in.UpdateChannelUseCase;
import com.devcool.domain.channel.port.in.command.CreateChannelCommand;
import com.devcool.domain.channel.port.in.command.UpdateChannelCommand;
import com.devcool.domain.common.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/channels")
@RequiredArgsConstructor
public class ChannelController {
  private final ChannelDtoMapper mapper;
  private final CreateChannelUseCase channelCreator;
  private final UpdateChannelUseCase channelUpdater;

  @PostMapping("/")
  ResponseEntity<ApiSuccessResponse<CreateChannelResponse>> createChannel(
      @Valid @RequestBody CreateChannelRequest request, Authentication auth) {
    Integer userId = Integer.valueOf(auth.getName());
    CreateChannelCommand command = mapper.toCreateChannelCommand(request, userId);
    Integer channelId = channelCreator.createChannel(command);
    CreateChannelResponse response = mapper.toCreateChannelResponse(channelId);
    return ResponseEntity.created(URI.create("/api/v1/channels"))
        .body(
            ApiResponseFactory.success(
                HttpStatus.CREATED,
                ErrorCode.CREATED.code(),
                "Create channel successfully",
                response));
  }

  @PatchMapping("/{channelId}")
  ResponseEntity<ApiSuccessResponse<UpdateChannelResponse>> updateChannel(
      @Valid @RequestBody UpdateChannelRequest request,
      @PathVariable Integer channelId
  ) {
    UpdateChannelCommand command = mapper.toUpdateChannelCommand(request);
    boolean isChannelUpdated = channelUpdater.updateChannel(channelId, command);
    UpdateChannelResponse response = mapper.toUpdateChannelResponse(isChannelUpdated);
    return ResponseEntity.ok(
        ApiResponseFactory.success(
            HttpStatus.OK, ErrorCode.OK.code(), "Update channel successfully", response));
  }
}
