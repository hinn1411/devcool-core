package com.devcool.application.service;

import com.devcool.domain.media.exception.InvalidMediaContentException;
import com.devcool.domain.media.exception.InvalidObjectKeyException;
import com.devcool.domain.media.exception.UnsupportedMediaTypeException;
import com.devcool.domain.media.port.in.GetMediaUrlUseCase;
import com.devcool.domain.media.port.in.UploadMediaUseCase;
import com.devcool.domain.media.port.in.command.UploadMediaCommand;
import com.devcool.domain.media.port.out.MediaStoragePort;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class MediaService implements UploadMediaUseCase, GetMediaUrlUseCase {

  private static final Set<String> allowedContentTypes =
      Set.of("image/jpeg", "image/png", "image/webp", "video/mp4");
  private static final Duration DEFAULT_PRESIGN_TTL = Duration.ofMinutes(10);
  private final MediaStoragePort storagePort;
  private static final Logger log = LoggerFactory.getLogger(MediaService.class);

  @Override
  public String upload(UploadMediaCommand command) {
    MultipartFile file = command.file();

    if (file.isEmpty()) {
      log.warn("Media content is null!");
      throw new InvalidMediaContentException();
    }

    if (isContentTypeInvalid(file.getContentType())) {
      log.warn("Content type: {}  is invalid!", command.contentType());
      throw new UnsupportedMediaTypeException(command.contentType());
    }
    String mediaKey = buildMediaKey(command.channelId(), file.getOriginalFilename());
    try (InputStream in = file.getInputStream()) {
      MediaStoragePort.UploadRequest uploadRequest =
          new MediaStoragePort.UploadRequest(mediaKey, in, file.getSize(), file.getContentType());
      storagePort.upload(uploadRequest);
      return mediaKey;
    } catch (IOException e) {
      throw new RuntimeException("Failed to read upload stream", e);
    }
  }

  private boolean isContentTypeInvalid(String contentType) {
    return Objects.isNull(contentType) || !allowedContentTypes.contains(contentType);
  }

  @Override
  public PresignedUrlResult getPresignedUrl(String objectKey) {
    if (Objects.isNull(objectKey) || objectKey.isBlank()) {
      throw new InvalidObjectKeyException();
    }

    MediaStoragePort.PresignedGetResult presignedResult =
        storagePort.presignGet(
            new MediaStoragePort.PresignGetRequest(objectKey, DEFAULT_PRESIGN_TTL));
    return new PresignedUrlResult(presignedResult.url(), presignedResult.expiresAt());
  }

  private String buildMediaKey(Integer channelId, String fileName) {
    String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));

    String extension = extractExtension(fileName);

    String uuid = UUID.randomUUID().toString();

    return String.format("channel/%d/%s/%s%s", channelId, datePath, uuid, extension);
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
