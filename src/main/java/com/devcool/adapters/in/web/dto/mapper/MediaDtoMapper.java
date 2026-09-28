package com.devcool.adapters.in.web.dto.mapper;

import com.devcool.domain.media.port.in.command.UploadMediaCommand;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.springframework.web.multipart.MultipartFile;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface MediaDtoMapper {

  @Mapping(target = "file", source = "file")
  @Mapping(target = "size", source = "file.size")
  @Mapping(target = "contentType", source = "file.contentType")
  UploadMediaCommand toUploadMediaCommand(MultipartFile file, Integer userId, Integer channelId);
}
