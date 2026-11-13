package com.devcool.adapters.out.persistence.reaction.repository;

import com.devcool.adapters.out.persistence.reaction.entity.MessageReactionEntity;
import com.devcool.adapters.out.persistence.reaction.projection.ReactionCountRow;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageReactionRepository
    extends JpaRepository<MessageReactionEntity, MessageReactionEntity.Key> {

  /** Idempotent via the PK, also under concurrent duplicates. */
  @Modifying
  @Query(
      value =
          """
          insert into message_reaction (message_id, user_id, emoji, created_time)
          values (:messageId, :userId, :emoji, :createdTime)
          on conflict (message_id, user_id, emoji) do nothing
          """,
      nativeQuery = true)
  int insertIfAbsent(
      @Param("messageId") Long messageId,
      @Param("userId") Long userId,
      @Param("emoji") String emoji,
      @Param("createdTime") Instant createdTime);

  @Query(
      value = "select distinct emoji from message_reaction where message_id = :messageId",
      nativeQuery = true)
  List<String> findDistinctEmojis(@Param("messageId") Long messageId);

  @Modifying
  @Query(
      value =
          """
          delete from message_reaction
          where message_id = :messageId and user_id = :userId and emoji = :emoji
          """,
      nativeQuery = true)
  int deleteOwn(
      @Param("messageId") Long messageId,
      @Param("userId") Long userId,
      @Param("emoji") String emoji);

  /** One statement per page; emojis by first use. */
  @Query(
      value =
          """
          select message_id               as messageId,
                 emoji                    as emoji,
                 count(*)                 as total,
                 bool_or(user_id = :viewerId) as mine
          from message_reaction
          where message_id in (:messageIds)
          group by message_id, emoji
          order by message_id, min(created_time), emoji
          """,
      nativeQuery = true)
  List<ReactionCountRow> countByMessage(
      @Param("messageIds") Collection<Long> messageIds, @Param("viewerId") Long viewerId);
}
