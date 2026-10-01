package com.devcool.adapters.out.persistence.member.repository;

import com.devcool.adapters.out.persistence.member.entity.MemberEntity;
import com.devcool.domain.member.model.enums.MemberType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<MemberEntity, Integer> {

  List<MemberEntity> findByChannel_IdAndUser_IdIn(Integer channelId, Collection<Integer> userIds);

  boolean existsByChannel_IdAndUser_Id(Integer channelId, Integer userId);

  @Query(
      """
      SELECT m.role
      FROM MemberEntity m
      WHERE m.channel.id = :channelId AND m.user.id = :userId
      """)
  Optional<MemberType> findRoleByChannelIdAndUserId(
      @Param("channelId") Integer channelId, @Param("userId") Integer userId);
}
