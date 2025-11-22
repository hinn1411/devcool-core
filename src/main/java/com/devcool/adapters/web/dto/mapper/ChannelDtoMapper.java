package com.devcool.adapters.web.dto.mapper;

import com.devcool.adapters.web.dto.request.CreateChannelRequest;
import com.devcool.adapters.web.dto.response.CreateChannelResponse;
import com.devcool.domain.channel.port.in.command.CreateChannelCommand;
import org.springframework.stereotype.Component;

@Component
public class ChannelDtoMapper {

  public CreateChannelResponse toCreateChannelResponse(Integer channelId) {
    return CreateChannelResponse.builder().channelId(channelId).build();

  }
  public CreateChannelCommand toCreateChannelCommand(CreateChannelRequest request, Integer creatorId) {
    return new CreateChannelCommand(request.name(),
        request.boundaryType(),
        request.expiredTime(),
        request.channelType(),
        creatorId,
        request.leader());
  }
}
