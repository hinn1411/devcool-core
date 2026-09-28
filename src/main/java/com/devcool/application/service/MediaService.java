package com.devcool.application.service;

import com.devcool.domain.media.exception.InvalidMediaContentException;
import com.devcool.domain.media.exception.InvalidObjectKeyException;
import com.devcool.domain.media.exception.MediaTooLargeException;
import com.devcool.domain.media.exception.UnsupportedMediaTypeException;
import com.devcool.domain.media.model.MediaKind;
import com.devcool.domain.media.port.in.GetMediaUrlUseCase;
import com.devcool.domain.media.port.in.UploadMediaUseCase;
import com.devcool.domain.media.port.in.command.UploadMediaCommand;
import com.devcool.domain.media.port.out.MediaStoragePort;
import com.devcool.domain.member.exception.MemberNotFoundException;
import com.devcool.domain.member.port.out.MemberPort;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class MediaService implements UploadMediaUseCase, GetMediaUrlUseCase {

  private static final Duration DEFAULT_PRESIGN_TTL = Duration.ofMinutes(10);
  private static final String ALLOWED_EXTENSIONS = "jpg|jpeg|png|webp|mp4";

  /**
   * Exactly the shape {@link #buildMediaKey} produces: {@code
   * channel/{channelId}/{yyyy/MM/dd}/{uuid}{ext}}. The channel id in the key is what authorises
   * access to the object, so a key that does not match in full is rejected rather than parsed
   * loosely.
   */
  private static final Pattern OBJECT_KEY_PATTERN =
      Pattern.compile(
          "channel/([1-9]\\d{0,8})"
              + "/\\d{4}/\\d{2}/\\d{2}"
              + "/[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}"
              + "(?:\\.(?:"
              + ALLOWED_EXTENSIONS
              + "))?");

  private final MediaStoragePort storagePort;
  private final MemberPort memberPort;
  private static final Logger log = LoggerFactory.getLogger(MediaService.class);

  @Override
  public String upload(UploadMediaCommand command) {
    requireMember(command.userId(), command.channelId());
    MultipartFile file = command.file();

    if (file.isEmpty()) {
      log.warn("Media content is null!");
      throw new InvalidMediaContentException();
    }

    MediaKind kind =
        MediaKind.fromContentType(file.getContentType())
            .orElseThrow(
                () -> {
                  log.warn("Content type: {}  is invalid!", command.contentType());
                  return new UnsupportedMediaTypeException(String.valueOf(command.contentType()));
                });

    if (!kind.isSizeAllowed(command.size())) {
      log.warn("{} size {} exceeds limit {}", kind, command.size(), kind.maxBytes());
      throw new MediaTooLargeException(command.size(), kind.maxBytes());
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

  @Override
  public PresignedUrlResult getPresignedUrl(Integer userId, String objectKey) {
    requireMember(userId, channelIdFrom(objectKey));

    MediaStoragePort.PresignedGetResult presignedResult =
        storagePort.presignGet(
            new MediaStoragePort.PresignGetRequest(objectKey, DEFAULT_PRESIGN_TTL));
    return new PresignedUrlResult(presignedResult.url(), presignedResult.expiresAt());
  }

  private void requireMember(Integer userId, Integer channelId) {
    if (!memberPort.existMemberOfChannelByUserId(channelId, userId)) {
      log.warn("User {} is not a member of channel {}", userId, channelId);
      throw new MemberNotFoundException(userId);
    }
  }

  private static Integer channelIdFrom(String objectKey) {
    if (Objects.nonNull(objectKey)) {
      Matcher matcher = OBJECT_KEY_PATTERN.matcher(objectKey);
      if (matcher.matches()) {
        return Integer.valueOf(matcher.group(1));
      }
    }
    log.warn("Rejected an object key that does not match the expected format");
    throw new InvalidObjectKeyException();
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
    if (!ext.matches("\\.(" + ALLOWED_EXTENSIONS + ")$")) {
      throw new UnsupportedMediaTypeException(ext);
    }

    return ext;
  }
}
