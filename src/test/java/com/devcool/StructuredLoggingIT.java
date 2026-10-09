package com.devcool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;

/**
 * The log format per profile (P1-T15). Boots an empty context with the real profile files, so it
 * needs no database and doesn't extend the Testcontainers base: logging is set up from the
 * profile's properties before any bean exists.
 */
@ExtendWith(OutputCaptureExtension.class)
class StructuredLoggingIT {

  private static final Logger log = LoggerFactory.getLogger(StructuredLoggingIT.class);
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Configuration(proxyBeanMethods = false)
  static class EmptyConfig {}

  @AfterEach
  void restoreDefaultLogging() {
    // Other ITs share this JVM and their cached contexts don't re-initialize logging, so put the
    // default text format back. The final cleanUp() keeps that format but drops Logback's
    // "already initialized" marker, so the next SpringApplication applies its own profile.
    LoggingSystem system = LoggingSystem.get(getClass().getClassLoader());
    system.cleanUp();
    system.beforeInitialize();
    system.initialize(new LoggingInitializationContext(new StandardEnvironment()), null, null);
    system.cleanUp();
    MDC.clear();
  }

  @Test
  void ecsProfile_writesOneJsonLinePerEvent_withMdcFieldsAndStackTrace(CapturedOutput output)
      throws Exception {
    logInsideContext("ecs");

    List<String> lines = linesMentioning(output, "IllegalStateException");
    assertThat(lines).as("the stack trace stays inside one JSON line").hasSize(1);
    JsonNode event = objectMapper.readTree(lines.getFirst());
    assertThat(event.path("message").asText()).isEqualTo("frame failed");
    assertThat(event.path("userId").asText()).isEqualTo("42");
    assertThat(event.path("connectionId").asText()).isEqualTo("conn-1");
    assertThat(event.path("service").path("name").asText()).isEqualTo("devcool");
    assertThat(event.path("error").path("stack_trace").asText()).contains("boom");
  }

  @Test
  void defaultProfile_staysHumanReadable(CapturedOutput output) {
    logInsideContext();

    List<String> lines = linesMentioning(output, "frame failed");
    assertThat(lines).hasSize(1);
    assertThat(lines.getFirst()).doesNotStartWith("{");
  }

  private void logInsideContext(String... profiles) {
    try (ConfigurableApplicationContext ignored =
        new SpringApplicationBuilder(EmptyConfig.class)
            .profiles(profiles)
            .web(WebApplicationType.NONE)
            .bannerMode(Banner.Mode.OFF)
            .logStartupInfo(false)
            .run()) {
      MDC.put("userId", "42");
      MDC.put("connectionId", "conn-1");
      log.error("frame failed", new IllegalStateException("boom"));
    }
  }

  private static List<String> linesMentioning(CapturedOutput output, String text) {
    return output.getOut().lines().filter(line -> line.contains(text)).toList();
  }
}
