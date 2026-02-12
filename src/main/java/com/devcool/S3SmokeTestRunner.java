package com.devcool;

import com.devcool.domain.media.port.out.MediaStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class S3SmokeTestRunner implements CommandLineRunner {
  private final MediaStoragePort storagePort;

  @Override
  public void run(String... args) {
    byte[] data = "Hello s3".getBytes(StandardCharsets.UTF_8);
    String key = "smoke/" + UUID.randomUUID() + ".txt";
    storagePort.upload(new MediaStoragePort.UploadRequest(
        key,
        new ByteArrayInputStream(data),
        data.length,
        "text/plain"
    ));

    System.out.println("Uploaded to S3 with key: " + key);
  }
}
