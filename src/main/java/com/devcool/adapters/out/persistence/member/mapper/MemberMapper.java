package com.devcool.adapters.out.persistence.member.mapper;


import com.devcool.adapters.out.persistence.entity.MemberEntity;
import com.devcool.domain.member.model.Member;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MemberMapper {
  Member toDomain(MemberEntity entity);

  MemberEntity toEntity(Member member);
}