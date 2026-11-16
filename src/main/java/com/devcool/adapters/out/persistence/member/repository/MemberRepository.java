package com.devcool.adapters.out.persistence.member.repository;

import com.devcool.adapters.out.persistence.member.entity.MemberEntity;
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

  /** Hard delete (P3-T11): the unique (user, channel) pair stays free for a later re-add. */
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
   * Forward-only, clamped read marker (P3-T10). Returns the matched row count: 0 when the user is
   * not a member of the channel. A no-op (older seq) still matches, so it returns 1.
   */
  @Modifying
  @Query(
      value =
          """
          UPDATE member
          SET last_read_seq = GREATEST(last_read_seq, LEAST(:seq, channel.last_seq))
          FROM channel
          WHERE member.user_id = :userId and member.channel_id = :channelId and member.channel_id = channel.id
          """,
      nativeQuery = true)
  int advanceLastReadSeq(
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
