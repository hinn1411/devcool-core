package com.devcool.adapters.out.persistence.channel;

import com.devcool.adapters.out.persistence.channel.entity.ChannelEntity;
import com.devcool.adapters.out.persistence.channel.mapper.ChannelMapper;
import com.devcool.adapters.out.persistence.channel.repository.ChannelRepository;
import com.devcool.domain.channel.model.Channel;
import com.devcool.domain.channel.port.out.ChannelPort;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ChannelAdapter implements ChannelPort {
  private static final Logger log = LoggerFactory.getLogger(ChannelAdapter.class);
  private final ChannelRepository repo;
  private final ChannelMapper mapper;

  @Override
  public Integer save(Channel channel) {
    ChannelEntity entity = mapper.toEntity(channel);
    ChannelEntity saved = repo.save(entity);
    return saved.getId();
  }
}
