package com.devcool.adapters.in.web.controller;

import com.devcool.adapters.in.web.dto.mapper.ChannelDtoMapper;
import com.devcool.adapters.in.web.dto.request.AddMembersRequest;
import com.devcool.adapters.in.web.dto.request.CreateChannelRequest;
import com.devcool.adapters.in.web.dto.request.MarkReadRequest;
import com.devcool.adapters.in.web.dto.request.TransferLeaderRequest;
import com.devcool.adapters.in.web.dto.request.UpdateChannelRequest;
import com.devcool.adapters.in.web.dto.response.AddMembersResponse;
import com.devcool.adapters.in.web.dto.response.CreateChannelResponse;
import com.devcool.adapters.in.web.dto.response.GetChannelResponse;
import com.devcool.adapters.in.web.dto.response.UpdateChannelResponse;
import com.devcool.adapters.in.web.dto.wrapper.ApiSuccessResponse;
import com.devcool.adapters.in.web.util.ApiResponseFactory;
import com.devcool.domain.channel.model.ChannelListPage;
import com.devcool.domain.channel.port.in.CreateChannelUseCase;
import com.devcool.domain.channel.port.in.GetChannelQuery;
import com.devcool.domain.channel.port.in.LeaveChannelUseCase;
import com.devcool.domain.channel.port.in.MarkChannelReadUseCase;
import com.devcool.domain.channel.port.in.RemoveMemberUseCase;
import com.devcool.domain.channel.port.in.TransferLeaderUseCase;
import com.devcool.domain.channel.port.in.UpdateChannelUseCase;
import com.devcool.domain.channel.port.in.command.AddMembersCommand;
import com.devcool.domain.channel.port.in.command.CreateChannelCommand;
import com.devcool.domain.channel.port.in.command.GetChannelCommand;
import com.devcool.domain.channel.port.in.command.LeaveChannelCommand;
import com.devcool.domain.channel.port.in.command.MarkChannelReadCommand;
import com.devcool.domain.channel.port.in.command.RemoveMemberCommand;
import com.devcool.domain.channel.port.in.command.TransferLeaderCommand;
import com.devcool.domain.channel.port.in.command.UpdateChannelCommand;
import com.devcool.domain.common.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/channels")
@RequiredArgsConstructor
public class ChannelController {
  private final ChannelDtoMapper mapper;
  private final CreateChannelUseCase channelCreator;
  private final UpdateChannelUseCase channelUpdater;
  private final GetChannelQuery channelQuerier;
  private final MarkChannelReadUseCase channelReader;
  private final RemoveMemberUseCase memberRemover;
  private final LeaveChannelUseCase channelLeaver;
  private final TransferLeaderUseCase leaderTransferrer;

  @PostMapping
  ResponseEntity<ApiSuccessResponse<CreateChannelResponse>> createChannel(
      @Valid @RequestBody CreateChannelRequest request, Authentication auth) {
    Long userId = Long.valueOf(auth.getName());
    CreateChannelCommand command = mapper.toCreateChannelCommand(request, userId);
    Long channelId = channelCreator.createChannel(command);
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
      @PathVariable Long channelId,
      Authentication auth) {
    Long callerId = Long.valueOf(auth.getName());
    UpdateChannelCommand command = mapper.toUpdateChannelCommand(request, callerId);
    boolean isChannelUpdated = channelUpdater.updateChannel(channelId, command);
    UpdateChannelResponse response = mapper.toUpdateChannelResponse(isChannelUpdated);
    return ResponseEntity.ok(
        ApiResponseFactory.success(
            HttpStatus.OK, ErrorCode.OK.code(), "Update channel successfully", response));
  }

  @PostMapping("/{channelId}/members")
  ResponseEntity<ApiSuccessResponse<AddMembersResponse>> addMembers(
      @Valid @RequestBody AddMembersRequest request,
      @PathVariable Long channelId,
      Authentication auth) {
    Long callerId = Long.valueOf(auth.getName());
    AddMembersCommand command = mapper.toAddMembersCommand(request, callerId);
    boolean isMemberAdded = channelUpdater.addMember(channelId, command);
    AddMembersResponse response = mapper.toAddMembersResponse(isMemberAdded);
    return ResponseEntity.ok(
        ApiResponseFactory.success(
            HttpStatus.OK, ErrorCode.OK.code(), "Add new members successfully", response));
  }

  @GetMapping
  ResponseEntity<ApiSuccessResponse<GetChannelResponse>> getChannels(
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int limit,
      Authentication auth) {
    Long userId = Long.valueOf(auth.getName());
    GetChannelCommand command = mapper.toGetChannelCommand(userId, cursor, limit);
    ChannelListPage channels = channelQuerier.getChannels(command);
    GetChannelResponse response = mapper.toGetChannelResponse(channels);
    return ResponseEntity.ok(
        ApiResponseFactory.success(
            HttpStatus.OK, ErrorCode.OK.code(), "Get channels by userId successfully", response));
  }

  @Operation(
      summary = "Mark a channel read up to a seq",
      description =
          "Forward-only and idempotent; a seq above the channel's last seq is clamped."
              + " Not a member or unknown channel: 404.")
  @PutMapping("/{channelId}/read")
  ResponseEntity<Void> markRead(
      @Valid @RequestBody MarkReadRequest request,
      @PathVariable Long channelId,
      Authentication auth) {
    Long callerId = Long.valueOf(auth.getName());
    channelReader.markRead(new MarkChannelReadCommand(channelId, callerId, request.seq()));
    return ResponseEntity.noContent().build();
  }

  @Operation(
      summary = "Remove a member",
      description =
          "LOUNGE: the creator; FORUM: the creator or leader; PRIVATE_CHAT: 400. The creator, the"
              + " forum leader and the caller can't be removed (400). Their messages stay.")
  @DeleteMapping("/{channelId}/members/{userId}")
  ResponseEntity<Void> removeMember(
      @PathVariable Long channelId, @PathVariable Long userId, Authentication auth) {
    Long callerId = Long.valueOf(auth.getName());
    memberRemover.removeMember(new RemoveMemberCommand(channelId, callerId, userId));
    return ResponseEntity.noContent().build();
  }

  @Operation(
      summary = "Leave a channel",
      description =
          "Any member; a FORUM leader must transfer first and a PRIVATE_CHAT can't be left (400).")
  @DeleteMapping("/{channelId}/members/me")
  ResponseEntity<Void> leave(@PathVariable Long channelId, Authentication auth) {
    Long callerId = Long.valueOf(auth.getName());
    channelLeaver.leave(new LeaveChannelCommand(channelId, callerId));
    return ResponseEntity.noContent().build();
  }

  @Operation(
      summary = "Transfer the forum leader role",
      description =
          "Current leader only (403). The target must be a member (403). A LEADER row becomes"
              + " MEMBER; a CREATOR row stays CREATOR. FORUM only (400).")
  @PutMapping("/{channelId}/leader")
  ResponseEntity<Void> transferLeader(
      @Valid @RequestBody TransferLeaderRequest request,
      @PathVariable Long channelId,
      Authentication auth) {
    Long callerId = Long.valueOf(auth.getName());
    leaderTransferrer.transferLeader(
        new TransferLeaderCommand(channelId, callerId, request.userId()));
    return ResponseEntity.noContent().build();
  }
}
