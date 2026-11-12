package com.devcool.adapters.out.persistence.message.mapper;

import com.devcool.adapters.out.persistence.media.mapper.MediaMapper;
import com.devcool.adapters.out.persistence.message.entity.MessageEntity;
import com.devcool.domain.chat.model.Message;
import com.devcool.domain.chat.model.MessageItem;
import com.devcool.domain.chat.model.ReplyPreview;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(
    componentModel = "spring",
    uses = {MediaMapper.class})
public interface MessageMapper {

  /**
   * The {@code user}, {@code channel} and {@code replyTo} associations are resolved by the adapter
   * with entity references, so the mapper must leave them alone.
   */
  @Mapping(target = "user", ignore = true)
  @Mapping(target = "channel", ignore = true)
  @Mapping(target = "replyTo", ignore = true)
  MessageEntity toEntity(Message message);

  @Mapping(target = "userId", source = "user.id")
  @Mapping(target = "senderName", source = "user.name")
  @Mapping(target = "senderAvatar", source = "user.avatar")
  @Mapping(target = "content", source = "entity", qualifiedByName = "visibleContent")
  @Mapping(target = "replyTo", source = "entity", qualifiedByName = "replyPreview")
  @Mapping(target = "reactions", ignore = true) // merged by MessageService from ReactionPort
  MessageItem toItem(MessageEntity entity);

  /** A deleted message is a tombstone: seq and sender stay, content and media path don't. */
  @Named("visibleContent")
  default String visibleContent(MessageEntity message) {
    if (isTombstone(message)) {
      return null;
    }
    return message.getMedia() != null ? message.getMedia().getPath() : message.getContent();
  }

  /** The parent above a reply, as it is now. A tombstone quotes nothing either. */
  @Named("replyPreview")
  default ReplyPreview replyPreview(MessageEntity reply) {
    MessageEntity parent = reply.getReplyTo();
    if (parent == null || isTombstone(reply)) {
      return null;
    }
    return ReplyPreview.of(
        parent.getId(),
        parent.getSeq(),
        parent.getUser().getId(),
        parent.getUser().getName(),
        parent.getContentType(),
        parent.getContent(),
        isTombstone(parent));
  }

  private static boolean isTombstone(MessageEntity message) {
    return message.getDeletedTime() != null;
  }
}
