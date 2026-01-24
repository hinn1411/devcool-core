package com.devcool.application.service.realtime;

import com.devcool.domain.realtime.port.in.WsSubscribeUseCase;
import com.devcool.domain.realtime.port.in.command.SubscribeCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WsSubscribeService implements WsSubscribeUseCase {
  @Override
  public void subscribe(SubscribeCommand command) {

  }
}
