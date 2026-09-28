package com.devcool.adapters.out.persistence.channel.repository;

import com.devcool.adapters.out.persistence.channel.entity.ChannelEntity;
import com.devcool.adapters.out.persistence.channel.projection.ChannelListRow;
import com.devcool.domain.channel.model.enums.ChannelType;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChannelRepository extends JpaRepository<ChannelEntity, Integer> {
  @Modifying
  @Query(
      """
      UPDATE ChannelEntity
      SET totalOfMembers = totalOfMembers + :delta
      WHERE id = :channelId
      """)
  int increaseTotalMembers(@Param("channelId") Integer channelId, @Param("delta") int delta);

  @Modifying
  @Query(
      """
      UPDATE ChannelEntity
      SET name = :name, channelType = :channelType, expiredTime = :expiredTime
      WHERE id = :id
      """)
  int updateChannelInfo(
      @Param("id") Integer id,
      @Param("name") String name,
      @Param("channelType") ChannelType channelType,
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
      @Param("memberId") Integer memberId, @Param("cursorId") Integer cursorId, Pageable pageable);
}
