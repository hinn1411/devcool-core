package com.devcool.adapters.in.web.dto.mapper;

import com.devcool.domain.media.port.in.command.UploadMediaCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class MediaDtoMapper {

  private static final Logger log = LoggerFactory.getLogger(MediaDtoMapper.class);

  public UploadMediaCommand toUploadMediaCommand(MultipartFile file,
                                                 Integer userId,
                                                 Integer channelId) {
      return new UploadMediaCommand(
          file,
          file.getSize(),
          file.getContentType(),
          userId,
          channelId
      );
  }
}
