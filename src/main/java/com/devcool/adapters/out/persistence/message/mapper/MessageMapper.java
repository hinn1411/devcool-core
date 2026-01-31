package com.devcool.adapters.out.persistence.message.mapper;

import com.devcool.adapters.out.persistence.message.entity.MessageEntity;
import com.devcool.domain.chat.model.Message;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MessageMapper {
  Message toDomain(MessageEntity entity);

  MessageEntity toEntity(Message message);
}
