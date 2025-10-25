package com.devcool.adapters.persistence.mapper;

import com.devcool.adapters.persistence.entity.UserEntity;
import com.devcool.domain.user.model.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {
    User toDomain(UserEntity userEntity);
    UserEntity toEntity(User user);
}
