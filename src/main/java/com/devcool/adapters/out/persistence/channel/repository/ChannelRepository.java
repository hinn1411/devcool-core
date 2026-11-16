package com.devcool.adapters.out.persistence.channel.repository;

import com.devcool.adapters.out.persistence.channel.entity.ChannelEntity;
import com.devcool.adapters.out.persistence.channel.projection.ChannelAccessLockedRow;
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

  /** Compare-and-set on the leader (P3-T11): 0 rows when the leader already changed. */
  @Modifying
  @Query(
      value =
          """
          UPDATE channel SET leader_id = :newLeaderId
          WHERE id = :channelId AND leader_id = :expectedLeaderId
          """,
      nativeQuery = true)
  int updateLeader(
      @Param("channelId") Long channelId,
      @Param("expectedLeaderId") Long expectedLeaderId,
      @Param("newLeaderId") Long newLeaderId);

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
   *
   * <p>{@code :datedCursor} replaces {@code :cursorAt IS NULL}, which Postgres can't type.
   */
  @Query(
      """
      SELECT new com.devcool.adapters.out.persistence.channel.projection.ChannelListRow(
      c.id,
      c.name,
      c.channelType,
      c.boundaryType,
      c.lastSeq - m.lastReadSeq,
      c.lastMessageAt,
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
      WHERE m.user.id = :memberId
        AND (
              :cursorId IS NULL
           OR (:datedCursor = TRUE
               AND (c.lastMessageAt < :cursorAt
                    OR (c.lastMessageAt = :cursorAt AND c.id < :cursorId)
                    OR c.lastMessageAt IS NULL))
           OR (:datedCursor = FALSE
               AND c.lastMessageAt IS NULL
               AND c.id < :cursorId)
        )
      ORDER BY c.lastMessageAt DESC NULLS LAST, c.id DESC
      """)
  List<ChannelListRow> findChannelPageByMemberId(
      @Param("memberId") Long memberId,
      @Param("cursorAt") Instant cursorAt,
      @Param("cursorId") Long cursorId,
      @Param("datedCursor") boolean datedCursor,
      Pageable pageable);

  @Query(
      """
      SELECT new com.devcool.adapters.out.persistence.channel.projection.ChannelAccessRow(
      c.channelType,
      c.totalOfMembers,
      c.leader.id
      )
      FROM ChannelEntity c
      WHERE c.id = :id
      """)
  Optional<ChannelAccessRow> findAccessRowById(@Param("id") Long id);

  /**
   * Locks the channel row until commit. {@code NO KEY UPDATE} conflicts with every other channel
   * UPDATE (count, leader, seq bump) but not with the key-share locks that FK inserts take.
   */
  @Query(
      value =
          """
          SELECT channel_type AS channelType, total_of_members AS totalOfMembers,
                 leader_id AS leaderId
          FROM channel WHERE id = :id
          FOR NO KEY UPDATE
          """,
      nativeQuery = true)
  Optional<ChannelAccessLockedRow> lockAccessRowById(@Param("id") Long id);

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
