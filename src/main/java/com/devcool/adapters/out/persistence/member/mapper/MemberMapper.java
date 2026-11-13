package com.devcool.adapters.out.persistence.member.mapper;

import com.devcool.adapters.out.persistence.member.entity.MemberEntity;
import com.devcool.domain.member.model.Member;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MemberMapper {
  Member toDomain(MemberEntity entity);

  @Mapping(target = "lastReadSeq", ignore = true)
  MemberEntity toEntity(Member member);
}
