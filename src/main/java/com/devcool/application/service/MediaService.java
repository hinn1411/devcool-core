package com.devcool.application.service;

import com.devcool.domain.media.exception.InvalidMediaContentException;
import com.devcool.domain.media.exception.UnsupportedMediaTypeException;
import com.devcool.domain.media.port.in.UploadMediaUseCase;
import com.devcool.domain.media.port.in.command.UploadMediaCommand;
import com.devcool.domain.media.port.out.MediaStoragePort;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaService implements UploadMediaUseCase {

  private static final Set<String> allowedContentTypes = Set.of(
      "image/jpeg", "image/png", "image/webp",
      "video/mp4"
  );
  private final MediaStoragePort storagePort;
  private static final Logger log = LoggerFactory.getLogger(MediaService.class);

  @Override
  public String upload(UploadMediaCommand command) {
    MultipartFile file = command.file();
    if (Objects.isNull(file.getContentType()) || !allowedContentTypes.contains(file.getContentType())) {
      log.warn("Content type: {}  is invalid!", command.contentType());
      throw new UnsupportedMediaTypeException(command.contentType());
    }


    if (file.isEmpty()) {
      log.warn("Media content is null!");
      throw new InvalidMediaContentException();
    }
    String mediaKey = buildMediaKey(String.valueOf(command.channelId()), file.getOriginalFilename());
    try (InputStream in = file.getInputStream()) {
      MediaStoragePort.UploadRequest uploadRequest = new MediaStoragePort.UploadRequest(
          mediaKey,
          in,
          file.getSize(),
          file.getContentType()
      );
      storagePort.upload(uploadRequest);
      return mediaKey;
    } catch (IOException e) {
      throw new RuntimeException("Failed to read upload stream", e);
    }
  }

  private String buildMediaKey(String channelId, String fileName) {
    String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));

    String extension = extractExtension(fileName);

    String uuid = UUID.randomUUID().toString();

    return String.format(
        "channel/%s/%s/%s%s",
        channelId,
        datePath,
        uuid,
        extension
    );
  }

  private String extractExtension(String filename) {
    if (Objects.isNull(filename) || !filename.contains(".")) {
      return "";
    }

    String ext = filename.substring(filename.lastIndexOf(".")).toLowerCase();

    // Optional: allowlist extensions
    if (!ext.matches("\\.(jpg|jpeg|png|webp|mp4)$")) {
      throw new UnsupportedMediaTypeException(ext);
    }

    return ext;
  }
}
