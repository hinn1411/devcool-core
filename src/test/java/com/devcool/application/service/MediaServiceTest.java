package com.devcool.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.devcool.domain.media.exception.InvalidMediaContentException;
import com.devcool.domain.media.exception.InvalidObjectKeyException;
import com.devcool.domain.media.exception.UnsupportedMediaTypeException;
import com.devcool.domain.media.port.in.GetMediaUrlUseCase.PresignedUrlResult;
import com.devcool.domain.media.port.in.command.UploadMediaCommand;
import com.devcool.domain.media.port.out.MediaStoragePort;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

  private static final int USER_ID = 1;
  private static final int CHANNEL_ID = 42;

  @Mock private MediaStoragePort storagePort;

  @InjectMocks private MediaService mediaService;

  // ── upload ──────────────────────────────────────────────────────────────────

  @Test
  void upload_withValidJpegFile_returnsKeyMatchingExpectedFormat() {
    stubUpload();
    UploadMediaCommand command = makeCommand("photo1.jpg", "image/jpeg");

    String key = mediaService.upload(command);

    assertThat(key)
        .matches("channel/%d/\\d{4}/\\d{2}/\\d{2}/[a-f0-9\\-]+\\.jpg".formatted(CHANNEL_ID));
  }

  @Test
  void upload_forwardsCorrectMetadataToStoragePort() {
    stubUpload();
    MockMultipartFile file = makeFile("video.mp4", "video/mp4");
    UploadMediaCommand command =
        new UploadMediaCommand(file, file.getSize(), file.getContentType(), USER_ID, 10);

    String key = mediaService.upload(command);

    ArgumentCaptor<MediaStoragePort.UploadRequest> captor =
        ArgumentCaptor.forClass(MediaStoragePort.UploadRequest.class);
    verify(storagePort).upload(captor.capture());

    MediaStoragePort.UploadRequest captured = captor.getValue();
    assertThat(captured.objectKey()).isEqualTo(key);
    assertThat(captured.contentType()).isEqualTo("video/mp4");
    assertThat(captured.contentLength()).isEqualTo(file.getSize());
  }

  @Test
  void upload_withEmptyFile_throwsInvalidMediaContentException() {
    MockMultipartFile empty = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);
    UploadMediaCommand command =
        new UploadMediaCommand(empty, 0, "image/jpeg", USER_ID, CHANNEL_ID);

    assertThatThrownBy(() -> mediaService.upload(command))
        .isInstanceOf(InvalidMediaContentException.class);
    verifyNoInteractions(storagePort);
  }

  @Test
  void upload_withUnsupportedContentType_throwsUnsupportedMediaTypeException() {
    UploadMediaCommand command = makeCommand("doc.pdf", "application/pdf");

    assertThatThrownBy(() -> mediaService.upload(command))
        .isInstanceOf(UnsupportedMediaTypeException.class);
    verifyNoInteractions(storagePort);
  }

  @Test
  void upload_withUnsupportedFileExtension_throwsUnsupportedMediaTypeException() {
    UploadMediaCommand command = makeCommand("image.bmp", "image/jpeg");

    assertThatThrownBy(() -> mediaService.upload(command))
        .isInstanceOf(UnsupportedMediaTypeException.class);
    verifyNoInteractions(storagePort);
  }

  // ── getPresignedUrl ──────────────────────────────────────────────────────────

  @Test
  void getPresignedUrl_withValidKey_returnsUrlAndExpiry() {
    Instant expiresAt = Instant.now().plus(Duration.ofMinutes(10));
    when(storagePort.presignGet(any()))
        .thenReturn(
            new MediaStoragePort.PresignedGetResult("https://s3.example.com/file.jpg", expiresAt));

    PresignedUrlResult result = mediaService.getPresignedUrl("channel/42/2026/03/08/uuid.jpg");

    assertThat(result.url()).isEqualTo("https://s3.example.com/file.jpg");
    assertThat(result.expiresAt()).isEqualTo(expiresAt);
  }

  @Test
  void getPresignedUrl_passesCorrectTtlToStoragePort() {
    when(storagePort.presignGet(any()))
        .thenReturn(
            new MediaStoragePort.PresignedGetResult(
                "https://s3.example.com/file.jpg", Instant.now()));

    mediaService.getPresignedUrl("some/key");

    ArgumentCaptor<MediaStoragePort.PresignGetRequest> captor =
        ArgumentCaptor.forClass(MediaStoragePort.PresignGetRequest.class);
    verify(storagePort).presignGet(captor.capture());
    assertThat(captor.getValue().objectKey()).isEqualTo("some/key");
    assertThat(captor.getValue().expiresIn()).isEqualTo(Duration.ofMinutes(10));
  }

  @Test
  void getPresignedUrl_withNullKey_throwsInvalidObjectKeyException() {
    assertThatThrownBy(() -> mediaService.getPresignedUrl(null))
        .isInstanceOf(InvalidObjectKeyException.class);
    verifyNoInteractions(storagePort);
  }

  @Test
  void getPresignedUrl_withBlankKey_throwsInvalidObjectKeyException() {
    assertThatThrownBy(() -> mediaService.getPresignedUrl("   "))
        .isInstanceOf(InvalidObjectKeyException.class);
    verifyNoInteractions(storagePort);
  }

  @Test
  void upload_withPngFile_returnsKeyMatchingExpectedFormat() {
    stubUpload();
    UploadMediaCommand command = makeCommand("image.png", "image/png");

    String key = mediaService.upload(command);

    assertThat(key)
        .matches("channel/%d/\\d{4}/\\d{2}/\\d{2}/[a-f0-9\\-]+\\.png".formatted(CHANNEL_ID));
  }

  @Test
  void upload_withWebpFile_returnsKeyMatchingExpectedFormat() {
    stubUpload();
    UploadMediaCommand command = makeCommand("image.webp", "image/webp");

    String key = mediaService.upload(command);

    assertThat(key)
        .matches("channel/%d/\\d{4}/\\d{2}/\\d{2}/[a-f0-9\\-]+\\.webp".formatted(CHANNEL_ID));
  }

  // ── helpers ──────────────────────────────────────────────────────────────────

  private static MockMultipartFile makeFile(String filename, String contentType) {
    return new MockMultipartFile("file", filename, contentType, "data".getBytes());
  }

  private static UploadMediaCommand makeCommand(String filename, String contentType) {
    MockMultipartFile file = makeFile(filename, contentType);
    return new UploadMediaCommand(file, file.getSize(), file.getContentType(), USER_ID, CHANNEL_ID);
  }

  private void stubUpload() {
    when(storagePort.upload(any()))
        .thenReturn(new MediaStoragePort.UploadResult("bucket", "someKey"));
  }
}
