package com.devcool.adapters.out.persistence.message.repository;

import com.devcool.adapters.out.persistence.message.entity.MessageEntity;
import com.devcool.adapters.out.persistence.message.projection.EditableMessageRow;
import com.devcool.adapters.out.persistence.message.projection.MessageKeyRow;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<MessageEntity, Long> {

  @Query(
      """
      SELECT ms
      FROM MessageEntity ms
      LEFT JOIN FETCH ms.media
      JOIN FETCH ms.user
      WHERE ms.channel.id = :channelId
        AND ms.deletedTime IS NULL
        AND (:cursorId IS NULL OR ms.id < :cursorId)
      ORDER BY ms.id DESC
      """)
  List<MessageEntity> findByChannelId(
      @Param("channelId") Long channelId, @Param("cursorId") Long cursorId, Pageable pageable);

  /** Range scan on uk_message_channel_seq, read backwards. */
  @Query(
      """
      SELECT ms
      FROM MessageEntity ms
      LEFT JOIN FETCH ms.media
      JOIN FETCH ms.user
      WHERE ms.channel.id = :channelId
        AND ms.deletedTime IS NULL
        AND (:beforeSeq IS NULL OR ms.seq < :beforeSeq)
      ORDER BY ms.seq DESC
      """)
  List<MessageEntity> findPageBeforeSeq(
      @Param("channelId") Long channelId, @Param("beforeSeq") Long beforeSeq, Pageable pageable);

  /** Range scan on uk_message_channel_seq, read forwards. */
  @Query(
      """
      SELECT ms
      FROM MessageEntity ms
      LEFT JOIN FETCH ms.media
      JOIN FETCH ms.user
      WHERE ms.channel.id = :channelId
        AND ms.deletedTime IS NULL
        AND ms.seq > :afterSeq
      ORDER BY ms.seq ASC
      """)
  List<MessageEntity> findPageAfterSeq(
      @Param("channelId") Long channelId, @Param("afterSeq") Long afterSeq, Pageable pageable);

  /** Served by the uk_message_user_client_msg index; no join, no media load. */
  @Query(
      """
      SELECT new com.devcool.adapters.out.persistence.message.projection.MessageKeyRow(
      ms.id,
      ms.channel.id,
      ms.seq,
      ms.createdTime
      )
      FROM MessageEntity ms
      WHERE ms.user.id = :senderId AND ms.clientMsgId = :clientMsgId
      """)
  Optional<MessageKeyRow> findKeyRow(
      @Param("senderId") Long senderId, @Param("clientMsgId") UUID clientMsgId);

  @Query(
      """
      SELECT new com.devcool.adapters.out.persistence.message.projection.EditableMessageRow(
      ms.id,
      ms.channel.id,
      ms.seq,
      ms.user.id,
      ms.contentType,
      ms.content,
      ms.version,
      ms.createdTime,
      ms.editedTime
      )
      FROM MessageEntity ms
      WHERE ms.id = :id AND ms.deletedTime IS NULL
      """)
  Optional<EditableMessageRow> findEditableRow(@Param("id") Long id);

  /** Compare-and-set on the client's version: one edit per version wins. */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      UPDATE MessageEntity ms
      SET ms.content = :content, ms.version = ms.version + 1, ms.editedTime = :editedTime
      WHERE ms.id = :id AND ms.version = :expected AND ms.deletedTime IS NULL
      """)
  int updateContentIfVersion(
      @Param("id") Long id,
      @Param("expected") int expected,
      @Param("content") String content,
      @Param("editedTime") Instant editedTime);
}
