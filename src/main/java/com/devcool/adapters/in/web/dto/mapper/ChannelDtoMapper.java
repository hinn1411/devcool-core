package com.devcool.adapters.in.web.dto.mapper;

import com.devcool.adapters.in.web.dto.request.AddMembersRequest;
import com.devcool.adapters.in.web.dto.request.CreateChannelRequest;
import com.devcool.adapters.in.web.dto.request.UpdateChannelRequest;
import com.devcool.adapters.in.web.dto.response.*;
import com.devcool.domain.channel.model.ChannelListPage;
import com.devcool.domain.channel.port.in.command.AddMembersCommand;
import com.devcool.domain.channel.port.in.command.CreateChannelCommand;
import com.devcool.domain.channel.port.in.command.GetChannelCommand;
import com.devcool.domain.channel.port.in.command.UpdateChannelCommand;
import org.springframework.stereotype.Component;

@Component
public class ChannelDtoMapper {

  public CreateChannelCommand toCreateChannelCommand(
      CreateChannelRequest request, Integer creatorId) {
    return new CreateChannelCommand(
        request.name(),
        request.boundaryType(),
        request.expiredTime(),
        request.channelType(),
        creatorId,
        request.leaderId(),
        request.memberIds());
  }

  public CreateChannelResponse toCreateChannelResponse(Integer channelId) {
    return CreateChannelResponse.builder().channelId(channelId).build();
  }

  public UpdateChannelCommand toUpdateChannelCommand(UpdateChannelRequest request) {
    return new UpdateChannelCommand(request.name(),
        request.boundaryType(),
        request.expiredTime(),
        request.channelType());
  }

  public UpdateChannelResponse toUpdateChannelResponse(boolean isChannelUpdated) {
    return UpdateChannelResponse.builder().channelUpdated(isChannelUpdated).build();
  }

  public AddMembersCommand toAddMembersCommand(AddMembersRequest request) {
    return new AddMembersCommand(request.userIds());
  }

  public AddMembersResponse toAddMembersResponse(boolean isMemberAdded) {
    return AddMembersResponse.builder().memberAdded(isMemberAdded).build();
  }

  public GetChannelCommand toGetChannelCommand(Integer memberId, Integer cursorId, Integer limit) {
    return new GetChannelCommand(memberId, cursorId, limit);
  }


  public GetChannelResponse toGetChannelResponse(ChannelListPage page) {
    return GetChannelResponse.builder()
        .channels(page.items().stream().map(item -> new ChannelListItemResponse(
            item.id(),
            item.name(),
            item.channelType().name(),
            item.boundaryType().name()
        )).toList())
        .nextCursorId(page.cursorId())
        .hasMore(page.hasMore()).build();
  }
}
