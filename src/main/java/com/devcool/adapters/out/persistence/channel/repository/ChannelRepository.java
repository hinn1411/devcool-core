package com.devcool.adapters.out.persistence.channel.repository;

import com.devcool.adapters.out.persistence.channel.entity.ChannelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChannelRepository extends JpaRepository<ChannelEntity, Integer> {
  @Modifying
  @Query("""
      UPDATE ChannelEntity 
      SET totalOfMembers = totalOfMembers + :delta
      WHERE id = :channelId 
      """)
  int increaseTotalMembers(@Param("channelId") Integer channelId, @Param("delta") int delta);
}
