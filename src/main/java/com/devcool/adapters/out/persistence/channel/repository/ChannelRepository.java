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

  @Query(
      """
      SELECT DISTINCT new com.devcool.adapters.out.persistence.channel.projection.ChannelListRow(
      c.id,
      c.name,
      c.channelType,
      c.boundaryType
      )
      FROM ChannelEntity c
      JOIN c.members m
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
}
