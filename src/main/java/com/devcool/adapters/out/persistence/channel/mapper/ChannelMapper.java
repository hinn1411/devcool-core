package com.devcool.adapters.out.persistence.channel.mapper;

import com.devcool.adapters.out.persistence.channel.entity.ChannelEntity;
import com.devcool.domain.channel.model.Channel;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ChannelMapper {

  Channel toDomain(ChannelEntity entity);

  ChannelEntity toEntity(Channel channel);
}
