package com.devcool.adapters.out.storage;

import com.devcool.adapters.out.storage.config.AwsProps;
import com.devcool.domain.media.port.out.MediaStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Component
@RequiredArgsConstructor
public class S3StorageAdapter implements MediaStoragePort {

  private final S3Client s3Client;
  private final S3Presigner s3Presigner;
  private final AwsProps awsProps;

  @Override
  public UploadResult upload(UploadRequest request) {
    String bucket = awsProps.s3().bucket();
    PutObjectRequest putRequest =
        PutObjectRequest.builder()
            .bucket(bucket)
            .key(request.objectKey())
            .contentType(request.contentType())
            .build();
    s3Client.putObject(
        putRequest, RequestBody.fromInputStream(request.inputStream(), request.contentLength()));
    return new UploadResult(bucket, request.objectKey());
  }

  @Override
  public PresignedGetResult presignGet(PresignGetRequest request) {
    String bucket = awsProps.s3().bucket();

    GetObjectRequest getRequest =
        GetObjectRequest.builder().bucket(bucket).key(request.objectKey()).build();

    GetObjectPresignRequest presignRequest =
        GetObjectPresignRequest.builder()
            .signatureDuration(request.expiresIn())
            .getObjectRequest(getRequest)
            .build();

    PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
    return new PresignedGetResult(presigned.url().toString(), presigned.expiration());
  }

  /** Signed headers: S3 rejects another type or size (403) and a second write (412). */
  @Override
  public PresignedPutResult presignPut(PresignPutRequest request) {
    PutObjectRequest putRequest =
        PutObjectRequest.builder()
            .bucket(awsProps.s3().bucket())
            .key(request.objectKey())
            .contentType(request.contentType())
            .contentLength(request.contentLength())
            .ifNoneMatch("*")
            .build();

    PresignedPutObjectRequest presigned =
        s3Presigner.presignPutObject(
            PutObjectPresignRequest.builder()
                .signatureDuration(request.expiresIn())
                .putObjectRequest(putRequest)
                .build());
    return new PresignedPutResult(presigned.url().toString(), presigned.expiration());
  }
}
