package com.devcool.adapters.out.storage.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(AwsProps.class)
public class S3ClientConfig {

  @Bean
  public S3Client s3Client(AwsProps awsProps) {
    return S3Client
        .builder()
        .region(Region.of(awsProps.region()))
        .credentialsProvider(DefaultCredentialsProvider.create())
        .build();
  }

  @Bean
  public S3Presigner s3Presigner(AwsProps awsProps) {
    return S3Presigner.builder()
        .region(Region.of(awsProps.region()))
        .credentialsProvider(DefaultCredentialsProvider.create())
        .build();
  }
}
