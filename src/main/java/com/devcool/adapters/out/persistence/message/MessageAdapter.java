package com.devcool.adapters.out.persistence.message;

import com.devcool.adapters.out.persistence.channel.entity.ChannelEntity;
import com.devcool.adapters.out.persistence.message.entity.MessageEntity;
import com.devcool.adapters.out.persistence.message.mapper.MessageMapper;
import com.devcool.adapters.out.persistence.message.repository.MessageRepository;
import com.devcool.adapters.out.persistence.user.entity.UserEntity;
import com.devcool.domain.chat.model.Message;
import com.devcool.domain.chat.model.MessageItem;
import com.devcool.domain.chat.model.MessageKey;
import com.devcool.domain.chat.port.out.MessagePort;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MessageAdapter implements MessagePort {
  private static final String CLIENT_MSG_ID_KEY = "uk_message_user_client_msg";

  private final MessageRepository repo;
  private final MessageMapper mapper;
  private final EntityManager em;

  @Override
  public Optional<Long> save(Message message) {
    MessageEntity entity = mapper.toEntity(message);
    entity.setUser(em.getReference(UserEntity.class, message.getSenderId()));
    entity.setChannel(em.getReference(ChannelEntity.class, message.getChannelId()));
    if (entity.getMedia() != null) {
      entity.getMedia().setMessage(entity);
    }
    try {
      // Flush now so a key collision surfaces here, where it can be reported, not at commit.
      return Optional.of(repo.saveAndFlush(entity).getId());
    } catch (DataIntegrityViolationException ex) {
      if (ex.getCause() instanceof ConstraintViolationException cve
          && CLIENT_MSG_ID_KEY.equalsIgnoreCase(cve.getConstraintName())) {
        return Optional.empty();
      }
      throw ex;
    }
  }

  @Override
  public Optional<MessageKey> findByClientMsgId(Long senderId, UUID clientMsgId) {
    return repo.findKeyRow(senderId, clientMsgId)
        .map(row -> new MessageKey(row.id(), row.channelId(), row.seq(), row.createdTime()));
  }

  @Override
  public List<MessageItem> findMessages(Long channelId, Long cursorId, Integer size) {
    Pageable page = PageRequest.of(0, size);
    return repo.findByChannelId(channelId, cursorId, page).stream().map(mapper::toItem).toList();
  }

  @Override
  public List<MessageItem> findBeforeSeq(Long channelId, Long beforeSeq, Integer size) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<MessageItem> findAfterSeq(Long channelId, Long afterSeq, Integer size) {
    throw new UnsupportedOperationException();
  }
}
