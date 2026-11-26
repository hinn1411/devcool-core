package com.devcool.adapters.out.persistence.member.repository;

import com.devcool.adapters.out.persistence.member.entity.MemberEntity;
import com.devcool.adapters.out.persistence.member.projection.ReadMarkerRow;
import com.devcool.domain.member.model.enums.MemberType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<MemberEntity, Long> {

  List<MemberEntity> findByChannel_IdAndUser_IdIn(Long channelId, Collection<Long> userIds);

  boolean existsByChannel_IdAndUser_Id(Long channelId, Long userId);

  @Query(
      """
      SELECT m.role
      FROM MemberEntity m
      WHERE m.channel.id = :channelId AND m.user.id = :userId
      """)
  Optional<MemberType> findRoleByChannelIdAndUserId(
      @Param("channelId") Long channelId, @Param("userId") Long userId);

  /** Hard delete, so the user can be re-added. */
  @Modifying
  @Query(
      """
      DELETE FROM MemberEntity m
      WHERE m.channel.id = :channelId AND m.user.id = :userId
      """)
  int deleteByChannelIdAndUserId(@Param("channelId") Long channelId, @Param("userId") Long userId);

  @Modifying
  @Query(
      """
      UPDATE MemberEntity m SET m.role = :role
      WHERE m.channel.id = :channelId AND m.user.id = :userId
      """)
  int updateRole(
      @Param("channelId") Long channelId,
      @Param("userId") Long userId,
      @Param("role") MemberType role);

  /**
   * Forward-only, clamped read marker (P3-T10) with its value before and after (P4-T04). Empty when
   * the user is not a member. A no-op (older seq) still matches, with both values equal. The CTE
   * locks the row first, so a concurrent advance can't make "before" stale.
   */
  @Query(
      value =
          """
          WITH old AS (
            SELECT last_read_seq FROM member
            WHERE user_id = :userId AND channel_id = :channelId
            FOR UPDATE
          )
          UPDATE member
          SET last_read_seq = GREATEST(member.last_read_seq, LEAST(:seq, channel.last_seq))
          FROM channel, old
          WHERE member.user_id = :userId and member.channel_id = :channelId and member.channel_id = channel.id
          RETURNING old.last_read_seq AS previousSeq, member.last_read_seq AS seq
          """,
      nativeQuery = true)
  Optional<ReadMarkerRow> advanceLastReadSeq(
      @Param("channelId") Long channelId, @Param("userId") Long userId, @Param("seq") long seq);

  /**
   * New members start caught up: copies {@code channel.last_seq} into their marker. Flushes first,
   * so the rows just queued by {@code saveAll} exist when this native UPDATE runs.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          """
          UPDATE member SET last_read_seq = channel.last_seq
          FROM channel
          WHERE channel.id = :channelId AND member.channel_id = channel.id AND member.user_id IN (:userIds)
          """,
      nativeQuery = true)
  int catchUpMembers(
      @Param("channelId") Long channelId, @Param("userIds") Collection<Long> userIds);
}
