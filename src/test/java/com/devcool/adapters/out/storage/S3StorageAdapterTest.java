package com.devcool.adapters.out.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devcool.adapters.out.storage.config.AwsProps;
import com.devcool.domain.media.port.out.MediaStoragePort;
import java.io.ByteArrayInputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@ExtendWith(MockitoExtension.class)
class S3StorageAdapterTest {

  private static final String BUCKET = "test-bucket";
  private static final String OBJECT_KEY = "channel/1/2026/03/08/uuid.jpg";
  private static final String PRESIGN_URL =
      "https://test-bucket.s3.amazonaws.com/" + OBJECT_KEY + "?sig=abc";

  @Mock private S3Client s3Client;
  @Mock private S3Presigner s3Presigner;

  private S3StorageAdapter adapter;

  @BeforeEach
  void setUp() {
    AwsProps awsProps = new AwsProps("ap-southeast-1", new AwsProps.S3Props(BUCKET));
    adapter = new S3StorageAdapter(s3Client, s3Presigner, awsProps);
  }

  // ── upload ──────────────────────────────────────────────────────────────────

  @Test
  void upload_sendsCorrectPutObjectRequestToS3() {
    stubPutObject();

    adapter.upload(anUploadRequest());

    ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(s3Client).putObject(captor.capture(), any(RequestBody.class));

    PutObjectRequest captured = captor.getValue();
    assertThat(captured.bucket()).isEqualTo(BUCKET);
    assertThat(captured.key()).isEqualTo(OBJECT_KEY);
    assertThat(captured.contentType()).isEqualTo("image/jpeg");
  }

  @Test
  void upload_returnsResultWithBucketAndObjectKey() {
    stubPutObject();

    MediaStoragePort.UploadResult result = adapter.upload(anUploadRequest());

    assertThat(result.bucket()).isEqualTo(BUCKET);
    assertThat(result.objectKey()).isEqualTo(OBJECT_KEY);
  }

  // ── presignGet ───────────────────────────────────────────────────────────────

  @Test
  void presignGet_buildsCorrectPresignRequestAndReturnsUrl() throws MalformedURLException {
    stubPresigner(PRESIGN_URL);
    MediaStoragePort.PresignGetRequest request =
        new MediaStoragePort.PresignGetRequest(OBJECT_KEY, Duration.ofMinutes(10));

    MediaStoragePort.PresignedGetResult result = adapter.presignGet(request);

    assertThat(result.url()).isEqualTo(PRESIGN_URL);
    assertThat(result.expiresAt()).isAfter(Instant.now().minus(Duration.ofSeconds(5)));
  }

  @Test
  void presignGet_passesObjectKeyAndTtlToPresigner() throws MalformedURLException {
    stubPresigner(PRESIGN_URL);
    MediaStoragePort.PresignGetRequest request =
        new MediaStoragePort.PresignGetRequest(OBJECT_KEY, Duration.ofMinutes(5));

    adapter.presignGet(request);

    ArgumentCaptor<GetObjectPresignRequest> captor =
        ArgumentCaptor.forClass(GetObjectPresignRequest.class);
    verify(s3Presigner).presignGetObject(captor.capture());
    assertThat(captor.getValue().signatureDuration()).isEqualTo(Duration.ofMinutes(5));
  }

  // ── helpers ──────────────────────────────────────────────────────────────────

  private static MediaStoragePort.UploadRequest anUploadRequest() {
    return new MediaStoragePort.UploadRequest(
        OBJECT_KEY, new ByteArrayInputStream("data".getBytes()), 4L, "image/jpeg");
  }

  private void stubPutObject() {
    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenReturn(PutObjectResponse.builder().build());
  }

  private void stubPresigner(String url) throws MalformedURLException {
    PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
    when(presigned.url()).thenReturn(new URL(url));
    when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);
  }
}
