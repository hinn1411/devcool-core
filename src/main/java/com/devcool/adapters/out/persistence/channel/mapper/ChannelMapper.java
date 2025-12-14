package com.devcool.adapters.out.persistence.channel.mapper;

import com.devcool.adapters.out.persistence.channel.entity.ChannelEntity;
import com.devcool.adapters.out.persistence.member.mapper.MemberMapper;
import com.devcool.domain.channel.model.Channel;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = {MemberMapper.class})
public interface ChannelMapper {

  Channel toDomain(ChannelEntity entity);

  ChannelEntity toEntity(Channel channel);
}
