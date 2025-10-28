package com.devcool.adapters.out.persistence.user.mapper;

import com.devcool.adapters.out.persistence.user.entity.UserEntity;
import com.devcool.domain.user.model.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {
    User toDomain(UserEntity userEntity);
    UserEntity toEntity(User user);
}
