package com.devcool.domain.media.port.out;

import java.io.InputStream;

public interface MediaStoragePort {

  UploadResult upload(UploadRequest request);

  record UploadRequest(
      String objectKey,
      InputStream inputStream,
      long contentLength,
      String contentType
  ) {}

  record UploadResult(
      String bucket,
      String objectKey
  ) {}
}
