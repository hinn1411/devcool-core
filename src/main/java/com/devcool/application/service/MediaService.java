package com.devcool.application.service;

import com.devcool.domain.media.exception.InvalidMediaContentException;
import com.devcool.domain.media.port.in.UploadMediaUseCase;
import com.devcool.domain.media.port.in.command.UploadMediaCommand;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MediaService implements UploadMediaUseCase {

  private static final Logger log = LoggerFactory.getLogger(MediaService.class);

  @Override
  public boolean upload(UploadMediaCommand command) {
    if (Objects.isNull(command.content())) {
      log.warn("Media content is null!");
      throw new InvalidMediaContentException();
    }
    return true;
  }
}
