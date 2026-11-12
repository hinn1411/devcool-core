package com.devcool.adapters.out.persistence.message.mapper;

import com.devcool.adapters.out.persistence.media.mapper.MediaMapper;
import com.devcool.adapters.out.persistence.message.entity.MessageEntity;
import com.devcool.domain.chat.model.Message;
import com.devcool.domain.chat.model.MessageItem;
import com.devcool.domain.chat.model.ReplyPreview;
import com.devcool.domain.chat.model.enums.ContentType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(
    componentModel = "spring",
    uses = {MediaMapper.class})
public interface MessageMapper {

  /**
   * The {@code user} and {@code channel} associations are resolved by the adapter with entity
   * references, so the mapper must leave them alone.
   */
  @Mapping(target = "user", ignore = true)
  @Mapping(target = "channel", ignore = true)
  @Mapping(target = "replyTo", ignore = true)
  MessageEntity toEntity(Message message);

  /** A deleted message is a tombstone: seq and sender stay, content and media path don't. */
  @Mapping(target = "userId", source = "user.id")
  @Mapping(target = "senderName", source = "user.name")
  @Mapping(target = "senderAvatar", source = "user.avatar")
  @Mapping(
      target = "content",
      expression =
          "java(entity.getDeletedTime() != null ? null"
              + " : entity.getMedia() != null ? entity.getMedia().getPath() : entity.getContent())")
  MessageItem toItem(MessageEntity entity);

  int PREVIEW_CODE_POINTS = 100;

  /** The parent above a reply. Live state: an edit or delete after the reply shows here. */
  default ReplyPreview toReplyPreview(MessageEntity parent) {
    if (parent == null) {
      return null;
    }
    boolean deleted = parent.getDeletedTime() != null;
    boolean text =
        parent.getContentType() == ContentType.TEXT
            || parent.getContentType() == ContentType.MARKDOWN;
    return new ReplyPreview(
        parent.getId(),
        parent.getSeq(),
        parent.getUser().getId(),
        parent.getUser().getName(),
        parent.getContentType(),
        deleted || !text ? null : snippet(parent.getContent()),
        deleted);
  }

  /** Cut by code points, so an emoji (a surrogate pair) is never split in half. */
  private static String snippet(String content) {
    if (content == null || content.codePointCount(0, content.length()) <= PREVIEW_CODE_POINTS) {
      return content;
    }
    return content.substring(0, content.offsetByCodePoints(0, PREVIEW_CODE_POINTS));
  }
}
