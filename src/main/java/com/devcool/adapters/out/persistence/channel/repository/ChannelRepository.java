package com.devcool.adapters.out.persistence.channel.repository;

import com.devcool.adapters.out.persistence.channel.entity.ChannelEntity;
import com.devcool.adapters.out.persistence.channel.projection.ChannelAccessRow;
import com.devcool.adapters.out.persistence.channel.projection.ChannelListRow;
import com.devcool.domain.channel.model.enums.BoundaryType;
import com.devcool.domain.channel.model.enums.ChannelType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChannelRepository extends JpaRepository<ChannelEntity, Long> {
  @Modifying
  @Query(
      """
      UPDATE ChannelEntity
      SET totalOfMembers = totalOfMembers + :delta
      WHERE id = :channelId
      """)
  int increaseTotalMembers(@Param("channelId") Long channelId, @Param("delta") int delta);

  /** A null argument keeps the stored value. */
  @Modifying
  @Query(
      """
      UPDATE ChannelEntity c
      SET c.name = COALESCE(:name, c.name),
          c.channelType = COALESCE(:channelType, c.channelType),
          c.boundaryType = COALESCE(:boundaryType, c.boundaryType),
          c.expiredTime = COALESCE(:expiredTime, c.expiredTime)
      WHERE c.id = :id
      """)
  int updateChannelInfo(
      @Param("id") Long id,
      @Param("name") String name,
      @Param("channelType") ChannelType channelType,
      @Param("boundaryType") BoundaryType boundaryType,
      @Param("expiredTime") Instant expiredTime);

  /**
   * One statement per page (P3-T10): unread is a subtraction, and the newest message is found by
   * {@code seq = last_seq} through {@code UNIQUE(channel_id, seq)}. LEFT joins keep empty channels.
   */
  @Query(
      """
      SELECT new com.devcool.adapters.out.persistence.channel.projection.ChannelListRow(
      c.id,
      c.name,
      c.channelType,
      c.boundaryType,
      c.lastSeq - m.lastReadSeq,
      msg.id,
      msg.seq,
      sender.id,
      sender.name,
      msg.contentType,
      msg.content,
      msg.deletedTime
      )
      FROM ChannelEntity c
      JOIN c.members m
      LEFT JOIN MessageEntity msg ON msg.channel = c AND msg.seq = c.lastSeq
      LEFT JOIN msg.user sender
      WHERE m.user.id = :memberId AND (:cursorId IS NULL OR c.id < :cursorId)
      ORDER BY c.id DESC
      """)
  List<ChannelListRow> findChannelPageByMemberId(
      @Param("memberId") Long memberId, @Param("cursorId") Long cursorId, Pageable pageable);

  @Query(
      """
      SELECT new com.devcool.adapters.out.persistence.channel.projection.ChannelAccessRow(
      c.channelType,
      c.totalOfMembers
      )
      FROM ChannelEntity c
      WHERE c.id = :id
      """)
  Optional<ChannelAccessRow> findAccessRowById(@Param("id") Long id);

  /**
   * ADR-0012 bump; locks the channel row until commit. Not {@code @Modifying}: it returns a row.
   */
  @Query(
      value =
          """
          UPDATE channel SET last_seq = last_seq + 1, last_message_at = now()
          WHERE id = :channelId
          RETURNING last_seq
          """,
      nativeQuery = true)
  Optional<Long> bumpLastSeq(@Param("channelId") Long channelId);
}
