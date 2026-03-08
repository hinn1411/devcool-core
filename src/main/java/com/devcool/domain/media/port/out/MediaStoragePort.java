package com.devcool.domain.media.port.out;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;

public interface MediaStoragePort {

  UploadResult upload(UploadRequest request);

  PresignedGetResult presignGet(PresignGetRequest request);

  record UploadRequest(
      String objectKey, InputStream inputStream, long contentLength, String contentType) {}

  record UploadResult(String bucket, String objectKey) {}

  record PresignGetRequest(String objectKey, Duration expiresIn) {}

  record PresignedGetResult(String url, Instant expiresAt) {}
}
