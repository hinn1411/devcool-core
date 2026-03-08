package com.devcool.adapters.out.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws")
public record AwsProps(String region, S3Props s3) {
  public record S3Props(String bucket) {}
}
