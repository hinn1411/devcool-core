package com.devcool.adapter.out.mapper;

import com.devcool.adapter.out.persistence.UserEntity;
import com.devcool.domain.model.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {
    User toDomain(UserEntity userEntity);
    UserEntity toEntity(User user);
}
