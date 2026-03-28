package com.devcool.adapters.out.persistence.media;

import com.devcool.adapters.out.persistence.media.mapper.MediaMapper;
import com.devcool.adapters.out.persistence.media.repository.MediaRepository;
import com.devcool.domain.media.model.Media;
import com.devcool.domain.media.port.out.MediaPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class MediaAdapter implements MediaPort {
  private final MediaMapper mapper;
  private final MediaRepository repo;

  @Override
  @Transactional
  public Integer save(Media media) {
    return repo.save(mapper.toEntity(media)).getId();
  }
}
