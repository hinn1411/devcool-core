package com.devcool.domain.media.port.out;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;

public interface MediaStoragePort {

  UploadResult upload(UploadRequest request);

  PresignedGetResult presignGet(PresignGetRequest request);

  /** Only a PUT with exactly this type and length (and no existing object) succeeds. */
  PresignedPutResult presignPut(PresignPutRequest request);

  record UploadRequest(
      String objectKey, InputStream inputStream, long contentLength, String contentType) {}

  record UploadResult(String bucket, String objectKey) {}

  record PresignGetRequest(String objectKey, Duration expiresIn) {}

  record PresignedGetResult(String url, Instant expiresAt) {}

  record PresignPutRequest(
      String objectKey, String contentType, long contentLength, Duration expiresIn) {}

  record PresignedPutResult(String url, Instant expiresAt) {}
}
