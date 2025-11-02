package com.devcool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DevCoolApplication {
  private static final Logger log = LoggerFactory.getLogger(DevCoolApplication.class);

  public static void main(String[] args) {
    SpringApplication.run(DevCoolApplication.class, args);
  }
}
