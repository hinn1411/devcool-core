package com.devcool.adapters.out.persistence.message;

import com.devcool.adapters.out.persistence.message.mapper.MessageMapper;
import com.devcool.adapters.out.persistence.message.repository.MessageRepository;
import com.devcool.domain.chat.model.Message;
import com.devcool.domain.chat.port.out.MessagePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MessageAdapter implements MessagePort {
  private final MessageRepository repo;
  private final MessageMapper mapper;

  @Override
  public Integer save(Message message) {
    return repo.save(mapper.toEntity(message)).getId();
  }
}
