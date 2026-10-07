package com.devcool.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.devcool.domain.media.exception.InvalidMediaContentException;
import com.devcool.domain.media.exception.InvalidObjectKeyException;
import com.devcool.domain.media.exception.UnsupportedMediaTypeException;
import com.devcool.domain.media.port.in.GetMediaUrlUseCase.PresignedUrlResult;
import com.devcool.domain.media.port.in.command.UploadMediaCommand;
import com.devcool.domain.media.port.out.MediaStoragePort;
import com.devcool.domain.member.exception.MemberNotFoundException;
import com.devcool.domain.member.port.out.MemberPort;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

  private static final int USER_ID = 1;
  private static final int CHANNEL_ID = 42;
  private static final String UUID_PART = "123e4567-e89b-12d3-a456-426614174000";
  private static final byte[] DATA = "data".getBytes();
  private static final String VALID_KEY = "channel/42/2026/03/08/" + UUID_PART + ".jpg";

  @Mock private MediaStoragePort storagePort;
  @Mock private MemberPort memberPort;

  @InjectMocks private MediaService mediaService;

  // ── upload ──────────────────────────────────────────────────────────────────

  @Test
  void upload_withValidJpegFile_returnsKeyMatchingExpectedFormat() {
    stubMember(CHANNEL_ID);
    stubUpload();
    UploadMediaCommand command = makeCommand("photo1.jpg", "image/jpeg");

    String key = mediaService.upload(command);

    assertThat(key)
        .matches("channel/%d/\\d{4}/\\d{2}/\\d{2}/[a-f0-9\\-]+\\.jpg".formatted(CHANNEL_ID));
  }

  @Test
  void upload_forwardsCorrectMetadataToStoragePort() {
    stubMember(10);
    stubUpload();
    UploadMediaCommand command = makeCommand("video.mp4", "video/mp4", 10);

    String key = mediaService.upload(command);

    ArgumentCaptor<MediaStoragePort.UploadRequest> captor =
        ArgumentCaptor.forClass(MediaStoragePort.UploadRequest.class);
    verify(storagePort).upload(captor.capture());

    MediaStoragePort.UploadRequest captured = captor.getValue();
    assertThat(captured.objectKey()).isEqualTo(key);
    assertThat(captured.contentType()).isEqualTo("video/mp4");
    assertThat(captured.contentLength()).isEqualTo(DATA.length);
  }

  @Test
  void upload_withEmptyFile_throwsInvalidMediaContentException() {
    stubMember(CHANNEL_ID);
    UploadMediaCommand command =
        new UploadMediaCommand(
            "empty.jpg",
            "image/jpeg",
            0,
            () -> new ByteArrayInputStream(new byte[0]),
            USER_ID,
            CHANNEL_ID);

    assertThatThrownBy(() -> mediaService.upload(command))
        .isInstanceOf(InvalidMediaContentException.class);
    verifyNoInteractions(storagePort);
  }

  @Test
  void upload_withUnsupportedContentType_throwsUnsupportedMediaTypeException() {
    stubMember(CHANNEL_ID);
    UploadMediaCommand command = makeCommand("doc.pdf", "application/pdf");

    assertThatThrownBy(() -> mediaService.upload(command))
        .isInstanceOf(UnsupportedMediaTypeException.class);
    verifyNoInteractions(storagePort);
  }

  @Test
  void upload_withUnsupportedFileExtension_throwsUnsupportedMediaTypeException() {
    stubMember(CHANNEL_ID);
    UploadMediaCommand command = makeCommand("image.bmp", "image/jpeg");

    assertThatThrownBy(() -> mediaService.upload(command))
        .isInstanceOf(UnsupportedMediaTypeException.class);
    verifyNoInteractions(storagePort);
  }

  @Test
  void upload_notAMemberOfTheChannel_throwsMemberNotFoundAndStoresNothing() {
    when(memberPort.existMemberOfChannelByUserId(CHANNEL_ID, USER_ID)).thenReturn(false);
    UploadMediaCommand command = makeCommand("photo1.jpg", "image/jpeg");

    assertThatThrownBy(() -> mediaService.upload(command))
        .isInstanceOf(MemberNotFoundException.class);

    verifyNoInteractions(storagePort);
  }

  @Test
  void upload_membershipIsCheckedAgainstTheTargetChannel() {
    // The caller may belong to channel 42, but is uploading into channel 9.
    UploadMediaCommand command = makeCommand("photo.jpg", "image/jpeg", 9);
    when(memberPort.existMemberOfChannelByUserId(9, USER_ID)).thenReturn(false);

    assertThatThrownBy(() -> mediaService.upload(command))
        .isInstanceOf(MemberNotFoundException.class);

    verify(memberPort).existMemberOfChannelByUserId(9, USER_ID);
    verifyNoMoreInteractions(memberPort);
    verifyNoInteractions(storagePort);
  }

  @Test
  void upload_notAMemberAndInvalidFile_isRefusedAsForbiddenFirst() {
    when(memberPort.existMemberOfChannelByUserId(CHANNEL_ID, USER_ID)).thenReturn(false);
    UploadMediaCommand command = makeCommand("doc.pdf", "application/pdf");

    assertThatThrownBy(() -> mediaService.upload(command))
        .isInstanceOf(MemberNotFoundException.class);
  }

  // ── getPresignedUrl ──────────────────────────────────────────────────────────

  @Test
  void getPresignedUrl_memberOfTheKeysChannel_returnsUrlAndExpiry() {
    Instant expiresAt = Instant.now().plus(Duration.ofMinutes(10));
    when(memberPort.existMemberOfChannelByUserId(CHANNEL_ID, USER_ID)).thenReturn(true);
    when(storagePort.presignGet(any()))
        .thenReturn(
            new MediaStoragePort.PresignedGetResult("https://s3.example.com/file.jpg", expiresAt));

    PresignedUrlResult result = mediaService.getPresignedUrl(USER_ID, VALID_KEY);

    assertThat(result.url()).isEqualTo("https://s3.example.com/file.jpg");
    assertThat(result.expiresAt()).isEqualTo(expiresAt);
  }

  @Test
  void getPresignedUrl_passesCorrectKeyAndTtlToStoragePort() {
    when(memberPort.existMemberOfChannelByUserId(CHANNEL_ID, USER_ID)).thenReturn(true);
    when(storagePort.presignGet(any()))
        .thenReturn(
            new MediaStoragePort.PresignedGetResult(
                "https://s3.example.com/file.jpg", Instant.now()));

    mediaService.getPresignedUrl(USER_ID, VALID_KEY);

    ArgumentCaptor<MediaStoragePort.PresignGetRequest> captor =
        ArgumentCaptor.forClass(MediaStoragePort.PresignGetRequest.class);
    verify(storagePort).presignGet(captor.capture());
    assertThat(captor.getValue().objectKey()).isEqualTo(VALID_KEY);
    assertThat(captor.getValue().expiresIn()).isEqualTo(Duration.ofMinutes(10));
  }

  @Test
  void getPresignedUrl_notAMemberOfTheKeysChannel_throwsMemberNotFoundAndSignsNothing() {
    when(memberPort.existMemberOfChannelByUserId(CHANNEL_ID, USER_ID)).thenReturn(false);

    assertThatThrownBy(() -> mediaService.getPresignedUrl(USER_ID, VALID_KEY))
        .isInstanceOf(MemberNotFoundException.class);

    verifyNoInteractions(storagePort);
  }

  @Test
  void getPresignedUrl_membershipIsCheckedAgainstTheChannelInTheKey() {
    // The caller belongs to channel 42, but the key is under channel 9.
    String otherChannelKey = "channel/9/2026/03/08/" + UUID_PART + ".jpg";
    when(memberPort.existMemberOfChannelByUserId(9, USER_ID)).thenReturn(false);

    assertThatThrownBy(() -> mediaService.getPresignedUrl(USER_ID, otherChannelKey))
        .isInstanceOf(MemberNotFoundException.class);

    verify(memberPort).existMemberOfChannelByUserId(9, USER_ID);
    verifyNoMoreInteractions(memberPort);
    verifyNoInteractions(storagePort);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(
      strings = {
        "",
        "   ",
        "some/key",
        "channel/abc/2026/03/08/" + UUID_PART + ".jpg",
        "channel/0/2026/03/08/" + UUID_PART + ".jpg",
        "channel/007/2026/03/08/" + UUID_PART + ".jpg",
        "channel/-5/2026/03/08/" + UUID_PART + ".jpg",
        "channel/99999999999/2026/03/08/" + UUID_PART + ".jpg",
        "channel/42/../9/2026/03/08/" + UUID_PART + ".jpg",
        "channel/42/2026/03/08/" + UUID_PART + ".jpg/../../../9/x.jpg",
        "channel/42/2026/03/08/" + UUID_PART + ".exe",
        "channel/42/2026/3/8/" + UUID_PART + ".jpg",
        "channel/42/2026/03/08/not-a-uuid.jpg",
        "prefix/channel/42/2026/03/08/" + UUID_PART + ".jpg",
        "channel/42/2026/03/08/" + UUID_PART + ".jpg?x=1"
      })
  void getPresignedUrl_malformedKey_throwsInvalidObjectKeyBeforeAnyLookup(String key) {
    assertThatThrownBy(() -> mediaService.getPresignedUrl(USER_ID, key))
        .isInstanceOf(InvalidObjectKeyException.class);

    verifyNoInteractions(memberPort, storagePort);
  }

  // Ties the parser to the generator: if buildMediaKey ever changes shape, this fails
  // instead of every legitimate media request turning into a 400.
  @ParameterizedTest
  @CsvSource({
    "photo.jpg,image/jpeg",
    "photo.jpeg,image/jpeg",
    "photo.png,image/png",
    "photo.webp,image/webp",
    "clip.mp4,video/mp4",
    "noextension,image/png"
  })
  void getPresignedUrl_acceptsEveryKeyTheUploadGenerates(String filename, String contentType) {
    stubMember(CHANNEL_ID);
    stubUpload();
    String key = mediaService.upload(makeCommand(filename, contentType));
    when(storagePort.presignGet(any()))
        .thenReturn(
            new MediaStoragePort.PresignedGetResult("https://s3.example.com/f", Instant.now()));

    assertThatCode(() -> mediaService.getPresignedUrl(USER_ID, key)).doesNotThrowAnyException();
  }

  // ── helpers ──────────────────────────────────────────────────────────────────

  private static UploadMediaCommand makeCommand(String filename, String contentType) {
    return makeCommand(filename, contentType, CHANNEL_ID);
  }

  private static UploadMediaCommand makeCommand(
      String filename, String contentType, int channelId) {
    return new UploadMediaCommand(
        filename,
        contentType,
        DATA.length,
        () -> new ByteArrayInputStream(DATA),
        USER_ID,
        channelId);
  }

  private void stubMember(int channelId) {
    when(memberPort.existMemberOfChannelByUserId(channelId, USER_ID)).thenReturn(true);
  }

  private void stubUpload() {
    when(storagePort.upload(any()))
        .thenReturn(new MediaStoragePort.UploadResult("bucket", "someKey"));
  }
}
