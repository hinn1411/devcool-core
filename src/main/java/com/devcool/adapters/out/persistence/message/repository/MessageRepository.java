package com.devcool.adapters.out.persistence.message.repository;

import com.devcool.adapters.out.persistence.message.entity.MessageEntity;
import com.devcool.adapters.out.persistence.message.projection.MessageKeyRow;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
