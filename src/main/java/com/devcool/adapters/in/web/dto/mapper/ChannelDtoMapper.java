package com.devcool.adapters.in.web.dto.mapper;

import com.devcool.adapters.in.web.dto.request.AddMembersRequest;
import com.devcool.adapters.in.web.dto.request.CreateChannelRequest;
import com.devcool.adapters.in.web.dto.request.UpdateChannelRequest;
import com.devcool.adapters.in.web.dto.response.AddMembersResponse;
import com.devcool.adapters.in.web.dto.response.ChannelListItemResponse;
import com.devcool.adapters.in.web.dto.response.CreateChannelResponse;
import com.devcool.adapters.in.web.dto.response.GetChannelResponse;
import com.devcool.adapters.in.web.dto.response.UpdateChannelResponse;
import com.devcool.domain.channel.model.ChannelListItem;
import com.devcool.domain.channel.model.ChannelListPage;
import com.devcool.domain.channel.port.in.command.AddMembersCommand;
import com.devcool.domain.channel.port.in.command.CreateChannelCommand;
import com.devcool.domain.channel.port.in.command.GetChannelCommand;
import com.devcool.domain.channel.port.in.command.UpdateChannelCommand;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ChannelDtoMapper {

  @Mapping(target = "creatorId", source = "creatorId")
  CreateChannelCommand toCreateChannelCommand(CreateChannelRequest request, Integer creatorId);

  @Mapping(target = "channelId", source = "channelId")
  CreateChannelResponse toCreateChannelResponse(Integer channelId);

  @Mapping(target = "callerId", source = "callerId")
  UpdateChannelCommand toUpdateChannelCommand(UpdateChannelRequest request, Integer callerId);

  // Boxed because MapStruct rejects primitive source parameters. Mapped by expression, not
  // source: MapStruct would otherwise emit a second, redundant null check that SpotBugs flags
  // (RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE).
  @Mapping(target = "channelUpdated", expression = "java(isChannelUpdated)")
  UpdateChannelResponse toUpdateChannelResponse(Boolean isChannelUpdated);

  @Mapping(target = "callerId", source = "callerId")
  AddMembersCommand toAddMembersCommand(AddMembersRequest request, Integer callerId);

  // Boxed + expression for the same reason as toUpdateChannelResponse.
  @Mapping(target = "memberAdded", expression = "java(isMemberAdded)")
  AddMembersResponse toAddMembersResponse(Boolean isMemberAdded);

  GetChannelCommand toGetChannelCommand(Integer memberId, Integer cursorId, Integer limit);

  @Mapping(target = "channels", source = "items")
  @Mapping(target = "nextCursorId", source = "cursorId")
  GetChannelResponse toGetChannelResponse(ChannelListPage page);

  ChannelListItemResponse toChannelListItemResponse(ChannelListItem item);
}
