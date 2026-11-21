package com.devcool.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.devcool.application.service.channel.ChannelAuthorizer;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.media.exception.InvalidMediaContentException;
import com.devcool.domain.media.exception.InvalidObjectKeyException;
import com.devcool.domain.media.exception.MediaTooLargeException;
import com.devcool.domain.media.exception.UnsupportedMediaTypeException;
import com.devcool.domain.media.port.in.GetMediaUrlUseCase.PresignedUrlResult;
import com.devcool.domain.media.port.in.PresignUploadUseCase.PresignedUpload;
import com.devcool.domain.media.port.in.command.PresignUploadCommand;
import com.devcool.domain.media.port.in.command.UploadMediaCommand;
import com.devcool.domain.media.port.out.MediaStoragePort;
import com.devcool.domain.member.exception.MemberNotFoundException;
import com.devcool.domain.member.port.out.MemberPort;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

  private static final long USER_ID = 1L;
  private static final long CHANNEL_ID = 42L;
  private static final String UUID_PART = "123e4567-e89b-12d3-a456-426614174000";
  private static final byte[] DATA = "data".getBytes();
  private static final String VALID_KEY = "channel/42/2026/03/08/" + UUID_PART + ".jpg";
  private static final long MB = 1024L * 1024L;
  private static final String PUT_URL = "https://s3.example.com/put?sig=abc";

  @Mock private MediaStoragePort storagePort;
  @Mock private ChannelPort channelPort;
  @Mock private MemberPort memberPort;

  private MediaService mediaService;

  @BeforeEach
  void setUp() {
    mediaService = new MediaService(storagePort, new ChannelAuthorizer(channelPort, memberPort));
  }

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
  void upload_uppercaseExtensionUnderTurkishLocale_isLowercasedLocaleIndependently() {
    stubMember(CHANNEL_ID);
    stubUpload();
    Locale original = Locale.getDefault();
    Locale.setDefault(Locale.forLanguageTag("tr-TR"));
    try {
      String key = mediaService.upload(makeCommand("PHOTO.JPG", "image/jpeg"));

      assertThat(key).endsWith(".jpg");
    } finally {
      Locale.setDefault(original);
    }
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
    when(memberPort.existMemberOfChannelByUserId(9L, USER_ID)).thenReturn(false);

    assertThatThrownBy(() -> mediaService.upload(command))
        .isInstanceOf(MemberNotFoundException.class);

    verify(memberPort).existMemberOfChannelByUserId(9L, USER_ID);
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
    when(memberPort.existMemberOfChannelByUserId(9L, USER_ID)).thenReturn(false);

    assertThatThrownBy(() -> mediaService.getPresignedUrl(USER_ID, otherChannelKey))
        .isInstanceOf(MemberNotFoundException.class);

    verify(memberPort).existMemberOfChannelByUserId(9L, USER_ID);
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

  // ── presignUpload (P3-T14) ───────────────────────────────────────────────────

  @ParameterizedTest
  @CsvSource({"image/jpeg,.jpg", "image/png,.png", "image/webp,.webp", "video/mp4,.mp4"})
  void presignUpload_member_returnsChannelKeyWithExtFromType_andSigns5MinPut(
      String contentType, String ext) {
    stubMember(CHANNEL_ID);
    Instant expiresAt = Instant.parse("2026-11-19T10:05:00Z");
    when(storagePort.presignPut(any()))
        .thenReturn(new MediaStoragePort.PresignedPutResult(PUT_URL, expiresAt));

    PresignedUpload result =
        mediaService.presignUpload(
            new PresignUploadCommand(USER_ID, CHANNEL_ID, contentType, 1024));

    assertThat(result.url()).isEqualTo(PUT_URL);
    assertThat(result.expiresAt()).isEqualTo(expiresAt);
    assertThat(result.key())
        .matches("channel/42/\\d{4}/\\d{2}/\\d{2}/[0-9a-f-]{36}" + ext.replace(".", "\\."));
    ArgumentCaptor<MediaStoragePort.PresignPutRequest> captor =
        ArgumentCaptor.forClass(MediaStoragePort.PresignPutRequest.class);
    verify(storagePort).presignPut(captor.capture());
    assertThat(captor.getValue())
        .isEqualTo(
            new MediaStoragePort.PresignPutRequest(
                result.key(), contentType, 1024, Duration.ofMinutes(5)));
  }

  @Test
  void presignUpload_keyIsAcceptedByPresignedGet() {
    stubMember(CHANNEL_ID);
    when(storagePort.presignPut(any()))
        .thenReturn(new MediaStoragePort.PresignedPutResult(PUT_URL, Instant.now()));
    when(storagePort.presignGet(any()))
        .thenReturn(new MediaStoragePort.PresignedGetResult("https://s3/get", Instant.now()));
    String key =
        mediaService
            .presignUpload(new PresignUploadCommand(USER_ID, CHANNEL_ID, "image/png", 10))
            .key();

    assertThatCode(() -> mediaService.getPresignedUrl(USER_ID, key)).doesNotThrowAnyException();
  }

  @Test
  void presignUpload_nonMember_signsNothing() {
    when(memberPort.existMemberOfChannelByUserId(9L, USER_ID)).thenReturn(false);
    PresignUploadCommand command = new PresignUploadCommand(USER_ID, 9L, "image/png", 10);

    assertThatThrownBy(() -> mediaService.presignUpload(command))
        .isInstanceOf(MemberNotFoundException.class);
    verifyNoInteractions(storagePort);
  }

  @ParameterizedTest
  @MethodSource("badUploads")
  void presignUpload_rejectsBadTypeOrSize(
      String contentType, long size, Class<? extends Throwable> expected) {
    stubMember(CHANNEL_ID);
    PresignUploadCommand command = new PresignUploadCommand(USER_ID, CHANNEL_ID, contentType, size);

    assertThatThrownBy(() -> mediaService.presignUpload(command)).isInstanceOf(expected);
    verifyNoInteractions(storagePort);
  }

  static Stream<Arguments> badUploads() {
    return Stream.of(
        Arguments.of("application/pdf", 10, UnsupportedMediaTypeException.class),
        Arguments.of(null, 10, UnsupportedMediaTypeException.class),
        Arguments.of("image/png", 0, InvalidMediaContentException.class),
        Arguments.of("image/png", -1, InvalidMediaContentException.class),
        Arguments.of("image/png", 5 * MB + 1, MediaTooLargeException.class),
        Arguments.of("video/mp4", 10 * MB, MediaTooLargeException.class));
  }

  @ParameterizedTest
  @CsvSource({"image/png,5242880", "video/mp4,10485759", "image/jpeg,1"})
  void presignUpload_acceptsExactLimits(String contentType, long size) {
    stubMember(CHANNEL_ID);
    when(storagePort.presignPut(any()))
        .thenReturn(new MediaStoragePort.PresignedPutResult(PUT_URL, Instant.now()));

    assertThatCode(
            () ->
                mediaService.presignUpload(
                    new PresignUploadCommand(USER_ID, CHANNEL_ID, contentType, size)))
        .doesNotThrowAnyException();
  }

  // ── helpers ──────────────────────────────────────────────────────────────────

  private static UploadMediaCommand makeCommand(String filename, String contentType) {
    return makeCommand(filename, contentType, CHANNEL_ID);
  }

  private static UploadMediaCommand makeCommand(
      String filename, String contentType, long channelId) {
    return new UploadMediaCommand(
        filename,
        contentType,
        DATA.length,
        () -> new ByteArrayInputStream(DATA),
        USER_ID,
        channelId);
  }

  private void stubMember(long channelId) {
    when(memberPort.existMemberOfChannelByUserId(channelId, USER_ID)).thenReturn(true);
  }

  private void stubUpload() {
    when(storagePort.upload(any()))
        .thenReturn(new MediaStoragePort.UploadResult("bucket", "someKey"));
  }
}
