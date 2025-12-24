package com.devcool.adapters.out.persistence.channel.mapper;

import com.devcool.adapters.out.persistence.channel.entity.ChannelEntity;
import com.devcool.adapters.out.persistence.member.entity.MemberEntity;
import com.devcool.adapters.out.persistence.member.mapper.MemberMapper;
import com.devcool.domain.channel.model.Channel;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(
    componentModel = "spring",
    uses = {MemberMapper.class})
public interface ChannelMapper {

  Channel toDomain(ChannelEntity entity);

  ChannelEntity toEntity(Channel channel);

  @AfterMapping
  default void linkMembers(@MappingTarget ChannelEntity channelEntity) {
    if (channelEntity.getMembers() == null) return;
    for (MemberEntity member : channelEntity.getMembers()) {
      member.setChannel(channelEntity);
    }
  }
}
