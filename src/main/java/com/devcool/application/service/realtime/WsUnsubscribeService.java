package com.devcool.application.service.realtime;

import com.devcool.domain.realtime.port.in.WsUnsubscribeUseCase;
import com.devcool.domain.realtime.port.in.command.UnsubscribeCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WsUnsubscribeService implements WsUnsubscribeUseCase {

  @Override
  public void unsubscribe(UnsubscribeCommand command) {

  }
}
