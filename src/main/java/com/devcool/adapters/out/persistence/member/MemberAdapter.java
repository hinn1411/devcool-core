package com.devcool.adapters.out.persistence.member;

import com.devcool.adapters.out.persistence.channel.entity.ChannelEntity;
import com.devcool.adapters.out.persistence.member.entity.MemberEntity;
import com.devcool.adapters.out.persistence.member.mapper.MemberMapper;
import com.devcool.adapters.out.persistence.member.repository.MemberRepository;
import com.devcool.adapters.out.persistence.user.entity.UserEntity;
import com.devcool.domain.member.model.Member;
import com.devcool.domain.member.port.out.MemberPort;
import com.devcool.domain.model.enums.MemberType;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class MemberAdapter implements MemberPort {
  private final MemberMapper mapper;
  private final MemberRepository repo;
  private final EntityManager em;

  @Override
  public List<Member> findMembersOfChannelByUserIds(Integer channelId, Set<Integer> userIds) {
    return repo.findByChannel_IdAndUser_IdIn(channelId, userIds).stream().map(mapper::toDomain).toList();
  }

  @Override
  public Optional<Member> findMemberOfChannelByUserId(Integer channelId, Integer userId) {
    return repo.findByChannel_IdAndUser_Id(channelId, userId).map(mapper::toDomain);
  }

  @Override
  public boolean addMembers(Integer channelId, Set<Integer> memberIds) {
    ChannelEntity channelRef = em.getReference(ChannelEntity.class, channelId);
    List<MemberEntity> memberEntities = memberIds.stream().map(memberId ->
        MemberEntity
            .builder()
            .channel(channelRef)
            .role(MemberType.MEMBER)
            .joinedTime(Instant.now())
            .user(em.getReference(UserEntity.class, memberId))
            .build()).toList();
    return !repo.saveAll(memberEntities).isEmpty();
  }
}
