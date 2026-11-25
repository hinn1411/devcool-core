package com.devcool.adapters.out.persistence.channel.repository;

import com.devcool.adapters.out.persistence.channel.entity.DirectChatEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DirectChatRepository
    extends JpaRepository<DirectChatEntity, DirectChatEntity.Key> {

  @Query(
      value =
          "select channel_id from direct_chat where user_low_id = :low and user_high_id = :high",
      nativeQuery = true)
  Optional<Long> findChannelId(@Param("low") Long low, @Param("high") Long high);

  /** 0 when the pair already has a channel. Flushes first: the FK needs the channel row. */
  @Modifying(flushAutomatically = true)
  @Query(
      value =
          """
          insert into direct_chat (user_low_id, user_high_id, channel_id)
          values (:low, :high, :channelId)
          on conflict (user_low_id, user_high_id) do nothing
          """,
      nativeQuery = true)
  int insertIfAbsent(
      @Param("low") Long low, @Param("high") Long high, @Param("channelId") Long channelId);
}
