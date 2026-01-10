package com.devcool.adapters.out.persistence.member.repository;

import com.devcool.adapters.out.persistence.member.entity.MemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MemberRepository extends JpaRepository<MemberEntity, Integer> {

  List<MemberEntity> findByChannel_IdAndUser_IdIn(Integer channelId, Collection<Integer> userIds);
}
