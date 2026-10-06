package com.devcool.application.service.channel;

import com.devcool.adapters.out.persistence.channel.entity.ChannelEntity;
import com.devcool.adapters.out.persistence.channel.repository.ChannelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChannelLookupService {

  private final ChannelRepository channelRepository;

  public ChannelEntity getChannel(Integer channelId, Integer userId) {
    return channelRepository
        .findById(channelId)
        .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + channelId));
  }
}
