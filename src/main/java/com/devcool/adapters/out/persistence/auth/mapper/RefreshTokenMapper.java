package com.devcool.adapters.out.persistence.auth.mapper;

import com.devcool.adapters.out.persistence.auth.entity.RefreshTokenEntity;
import com.devcool.domain.auth.model.RefreshToken;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RefreshTokenMapper {
  RefreshToken toDomain(RefreshTokenEntity refreshTokenEntity);

  RefreshTokenEntity toEntity(RefreshToken refreshToken);
}
