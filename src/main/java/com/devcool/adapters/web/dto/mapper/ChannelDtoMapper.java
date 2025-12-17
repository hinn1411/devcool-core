package com.devcool.adapters.web.dto.mapper;

import com.devcool.adapters.web.dto.request.CreateChannelRequest;
import com.devcool.adapters.web.dto.request.UpdateChannelRequest;
import com.devcool.adapters.web.dto.response.CreateChannelResponse;
import com.devcool.adapters.web.dto.response.UpdateChannelResponse;
import com.devcool.domain.channel.port.in.command.CreateChannelCommand;
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
}
