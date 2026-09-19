package com.devcool.adapters.out.persistence.media.mapper;

import com.devcool.adapters.out.persistence.media.entity.MediaEntity;
import com.devcool.domain.media.model.Media;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MediaMapper {
  Media toDomain(MediaEntity entity);

  @Mapping(target = "message", ignore = true)
  MediaEntity toEntity(Media media);
}
