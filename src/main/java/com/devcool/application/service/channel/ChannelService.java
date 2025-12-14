package com.devcool.application.service.channel;

import com.devcool.domain.channel.exception.InvalidChannelConfigException;
import com.devcool.domain.channel.model.enums.ChannelType;
import com.devcool.domain.channel.policy.ChannelCreationStrategy;
import com.devcool.domain.channel.port.in.CreateChannelUseCase;
import com.devcool.domain.channel.port.in.command.CreateChannelCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ChannelService implements CreateChannelUseCase {
  private static final Logger log = LoggerFactory.getLogger(ChannelService.class);
  private final Map<ChannelType, ChannelCreationStrategy> strategies;

  public ChannelService(List<ChannelCreationStrategy> strategies) {
    this.strategies = new EnumMap<>(ChannelType.class);
    strategies.forEach(
        strategy -> this.strategies.put(strategy.getSupportedType(), strategy)
    );
  }

  @Override
  public Integer createChannel(CreateChannelCommand command) {
    ChannelType type = command.channelType();

    ChannelCreationStrategy strategy = strategies.get(type);
    if (Objects.isNull(strategy)) {
      log.warn("No channel creation strategy registered for type null");
      throw new InvalidChannelConfigException("Unsupported channel type: " + type);
    }

    return strategy.createChannel(command);
  }
}
