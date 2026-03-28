package com.devcool.adapters.out.persistence.media.mapper;

import com.devcool.adapters.out.persistence.media.entity.MediaEntity;
import com.devcool.domain.media.model.Media;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MediaMapper {
  Media toDomain(MediaEntity entity);

  MediaEntity toEntity(Media media);
}
