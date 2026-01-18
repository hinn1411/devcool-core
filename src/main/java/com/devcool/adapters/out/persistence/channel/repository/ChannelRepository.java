package com.devcool.adapters.out.persistence.channel.repository;

import com.devcool.adapters.out.persistence.channel.entity.ChannelEntity;
import com.devcool.adapters.out.persistence.channel.projection.ChannelListRow;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChannelRepository extends JpaRepository<ChannelEntity, Integer> {
  @Modifying
  @Query("""
      UPDATE ChannelEntity 
      SET totalOfMembers = totalOfMembers + :delta
      WHERE id = :channelId 
      """)
  int increaseTotalMembers(@Param("channelId") Integer channelId, @Param("delta") int delta);

  @Query("""
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
  List<ChannelListRow> findChannelPageByMemberId(@Param("memberId") Integer memberId,
                                      @Param("cursorId") Integer cursorId,
                                      Pageable pageable);
}
