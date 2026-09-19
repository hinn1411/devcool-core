package com.devcool.adapters.out.persistence.message.repository;

import com.devcool.adapters.out.persistence.message.entity.MessageEntity;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<MessageEntity, Integer> {

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
      @Param("channelId") Integer channelId,
      @Param("cursorId") Integer cursorId,
      Pageable pageable);
}
