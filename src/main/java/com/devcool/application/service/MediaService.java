package com.devcool.application.service;

import com.devcool.application.service.channel.ChannelAuthorizer;
import com.devcool.domain.media.exception.InvalidMediaContentException;
import com.devcool.domain.media.exception.InvalidObjectKeyException;
import com.devcool.domain.media.exception.MediaTooLargeException;
import com.devcool.domain.media.exception.UnsupportedMediaTypeException;
import com.devcool.domain.media.model.MediaKey;
import com.devcool.domain.media.model.MediaKind;
import com.devcool.domain.media.port.in.GetMediaUrlUseCase;
import com.devcool.domain.media.port.in.PresignUploadUseCase;
import com.devcool.domain.media.port.in.UploadMediaUseCase;
import com.devcool.domain.media.port.in.command.PresignUploadCommand;
import com.devcool.domain.media.port.in.command.UploadMediaCommand;
import com.devcool.domain.media.port.out.MediaStoragePort;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MediaService implements UploadMediaUseCase, GetMediaUrlUseCase, PresignUploadUseCase {

  private static final Duration DEFAULT_PRESIGN_TTL = Duration.ofMinutes(10);

  /** A write capability: shorter than reads, still enough for 10 MB on a slow link. */
  private static final Duration UPLOAD_PRESIGN_TTL = Duration.ofMinutes(5);

  private final MediaStoragePort storagePort;
  private final ChannelAuthorizer channelAuthorizer;
  private static final Logger log = LoggerFactory.getLogger(MediaService.class);

  /** Deprecated path: the bytes stream through the API. Replaced by {@link #presignUpload}. */
  @Override
  public String upload(UploadMediaCommand command) {
    channelAuthorizer.requireMembership(command.channelId(), command.userId());
    requireAllowed(command.contentType(), command.size());

    String mediaKey =
        MediaKey.generate(
            command.channelId(), LocalDate.now(), extractExtension(command.filename()));
    try (InputStream in = command.content().open()) {
      MediaStoragePort.UploadRequest uploadRequest =
          new MediaStoragePort.UploadRequest(mediaKey, in, command.size(), command.contentType());
      storagePort.upload(uploadRequest);
      return mediaKey;
    } catch (IOException e) {
      throw new RuntimeException("Failed to read upload stream", e);
    }
  }

  /** membership → type/size → sign. The declared type and size are bound into the signature. */
  @Override
  public PresignedUpload presignUpload(PresignUploadCommand command) {
    channelAuthorizer.requireMembership(command.channelId(), command.userId());
    MediaKind kind = requireAllowed(command.contentType(), command.size());

    String key =
        MediaKey.generate(
            command.channelId(), LocalDate.now(), kind.extensionFor(command.contentType()));
    MediaStoragePort.PresignedPutResult signed =
        storagePort.presignPut(
            new MediaStoragePort.PresignPutRequest(
                key, command.contentType(), command.size(), UPLOAD_PRESIGN_TTL));
    return new PresignedUpload(signed.url(), key, signed.expiresAt());
  }

  @Override
  public PresignedUrlResult getPresignedUrl(Long userId, String objectKey) {
    channelAuthorizer.requireMembership(channelIdFrom(objectKey), userId);

    MediaStoragePort.PresignedGetResult presignedResult =
        storagePort.presignGet(
            new MediaStoragePort.PresignGetRequest(objectKey, DEFAULT_PRESIGN_TTL));
    return new PresignedUrlResult(presignedResult.url(), presignedResult.expiresAt());
  }

  /** Non-empty, an accepted content type, and within that kind's size limit. */
  private static MediaKind requireAllowed(String contentType, long size) {
    if (size <= 0) {
      log.warn("Media content is empty!");
      throw new InvalidMediaContentException();
    }
    MediaKind kind =
        MediaKind.fromContentType(contentType)
            .orElseThrow(
                () -> {
                  log.warn("Content type: {}  is invalid!", contentType);
                  return new UnsupportedMediaTypeException(String.valueOf(contentType));
                });
    if (!kind.isSizeAllowed(size)) {
      log.warn("{} size {} exceeds limit {}", kind, size, kind.maxBytes());
      throw new MediaTooLargeException(size, kind.maxBytes());
    }
    return kind;
  }

  private static Long channelIdFrom(String objectKey) {
    return MediaKey.channelIdOf(objectKey)
        .orElseThrow(
            () -> {
              log.warn("Rejected an object key that does not match the expected format");
              return new InvalidObjectKeyException();
            });
  }

  private static String extractExtension(String filename) {
    if (Objects.isNull(filename) || !filename.contains(".")) {
      return "";
    }

    String ext = filename.substring(filename.lastIndexOf(".")).toLowerCase(Locale.ROOT);

    if (!ext.matches("\\.(" + MediaKey.ALLOWED_EXTENSIONS + ")$")) {
      throw new UnsupportedMediaTypeException(ext);
    }

    return ext;
  }
}
