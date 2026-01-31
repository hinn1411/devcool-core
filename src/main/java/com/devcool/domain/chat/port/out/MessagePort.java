package com.devcool.domain.chat.port.out;

import com.devcool.domain.chat.model.Message;

public interface MessagePort {
  Integer save(Message message);
}
