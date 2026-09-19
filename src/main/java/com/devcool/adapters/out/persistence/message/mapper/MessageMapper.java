package com.devcool.adapters.out.persistence.message.mapper;

import com.devcool.adapters.out.persistence.media.mapper.MediaMapper;
import com.devcool.adapters.out.persistence.message.entity.MessageEntity;
import com.devcool.domain.chat.model.Message;
import com.devcool.domain.chat.model.MessageItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(
    componentModel = "spring",
    uses = {MediaMapper.class})
public interface MessageMapper {
  Message toDomain(MessageEntity entity);

  MessageEntity toEntity(Message message);

  @Mapping(target = "userId", source = "user.id")
  @Mapping(target = "senderName", source = "user.name")
  @Mapping(target = "senderAvatar", source = "user.avatar")
  @Mapping(
      target = "content",
      expression =
          "java(entity.getMedia() != null ? entity.getMedia().getPath() : entity.getContent())")
  MessageItem toItem(MessageEntity entity);
}
