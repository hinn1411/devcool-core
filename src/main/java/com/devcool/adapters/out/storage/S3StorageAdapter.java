package com.devcool.adapters.out.storage;

import com.devcool.adapters.out.storage.config.AwsProps;
import com.devcool.domain.media.port.out.MediaStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
@RequiredArgsConstructor
public class S3StorageAdapter implements MediaStoragePort {

  private final S3Client s3Client;
  private final AwsProps awsProps;

  @Override
  public UploadResult upload(UploadRequest request) {
    String bucket = awsProps.s3().bucket();
    PutObjectRequest putRequest = PutObjectRequest.builder()
        .bucket(bucket)
        .key(request.objectKey())
        .contentType(request.contentType())
        .build();
    s3Client.putObject(
        putRequest,
        RequestBody.fromInputStream(request.inputStream(), request.contentLength())
    );
    return new UploadResult(bucket, request.objectKey());
  }
}
