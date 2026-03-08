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

  @Mock private MediaStoragePort storagePort;

  @InjectMocks private MediaService mediaService;

  @Test
  void upload_withValidJpegFile_returnsKeyMatchingExpectedFormat() {
    MockMultipartFile file =
        new MockMultipartFile("file", "photo.jpg", "image/jpeg", "image-data".getBytes());
    UploadMediaCommand command =
        new UploadMediaCommand(file, file.getSize(), file.getContentType(), 1, 42);
    when(storagePort.upload(any()))
        .thenReturn(new MediaStoragePort.UploadResult("bucket", "someKey"));

    String key = mediaService.upload(command);

    assertThat(key).matches("channel/42/\\d{4}/\\d{2}/\\d{2}/[a-f0-9\\-]+\\.jpg");
  }

  @Test
  void upload_forwardsCorrectMetadataToStoragePort() {
    MockMultipartFile file =
        new MockMultipartFile("file", "video.mp4", "video/mp4", "video-data".getBytes());
    UploadMediaCommand command =
        new UploadMediaCommand(file, file.getSize(), file.getContentType(), 1, 10);
    when(storagePort.upload(any())).thenReturn(new MediaStoragePort.UploadResult("bucket", "key"));

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
    MockMultipartFile file = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);
    UploadMediaCommand command = new UploadMediaCommand(file, 0, "image/jpeg", 1, 1);

    assertThatThrownBy(() -> mediaService.upload(command))
        .isInstanceOf(InvalidMediaContentException.class);
    verifyNoInteractions(storagePort);
  }

  @Test
  void upload_withUnsupportedContentType_throwsUnsupportedMediaTypeException() {
    MockMultipartFile file =
        new MockMultipartFile("file", "doc.pdf", "application/pdf", "data".getBytes());
    UploadMediaCommand command =
        new UploadMediaCommand(file, file.getSize(), "application/pdf", 1, 1);

    assertThatThrownBy(() -> mediaService.upload(command))
        .isInstanceOf(UnsupportedMediaTypeException.class);
    verifyNoInteractions(storagePort);
  }

  @Test
  void upload_withUnsupportedFileExtension_throwsUnsupportedMediaTypeException() {
    MockMultipartFile file =
        new MockMultipartFile("file", "image.bmp", "image/jpeg", "data".getBytes());
    UploadMediaCommand command = new UploadMediaCommand(file, file.getSize(), "image/jpeg", 1, 1);

    assertThatThrownBy(() -> mediaService.upload(command))
        .isInstanceOf(UnsupportedMediaTypeException.class);
    verifyNoInteractions(storagePort);
  }

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
}
