package com.devcool.adapters.out.persistence.message;

import com.devcool.adapters.out.persistence.channel.entity.ChannelEntity;
import com.devcool.adapters.out.persistence.message.entity.MessageEntity;
import com.devcool.adapters.out.persistence.message.mapper.MessageMapper;
import com.devcool.adapters.out.persistence.message.repository.MessageRepository;
import com.devcool.adapters.out.persistence.user.entity.UserEntity;
import com.devcool.domain.chat.model.Message;
import com.devcool.domain.chat.model.MessageItem;
import com.devcool.domain.chat.port.out.MessagePort;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MessageAdapter implements MessagePort {
  private final MessageRepository repo;
  private final MessageMapper mapper;
  private final EntityManager em;

  @Override
  public Integer save(Message message) {
    MessageEntity entity = mapper.toEntity(message);
    entity.setUser(em.getReference(UserEntity.class, message.getSenderId()));
    entity.setChannel(em.getReference(ChannelEntity.class, message.getChannelId()));
    if (entity.getMedia() != null) {
      entity.getMedia().setMessage(entity);
    }
    return repo.save(entity).getId();
  }

  @Override
  public List<MessageItem> findMessages(Integer channelId, Integer cursorId, Integer size) {
    Pageable page = PageRequest.of(0, size);
    return repo.findByChannelId(channelId, cursorId, page).stream().map(mapper::toItem).toList();
  }
}
