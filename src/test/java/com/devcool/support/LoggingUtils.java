package com.devcool.support;

import org.slf4j.MDC;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.logging.LoggingSystemProperty;
import org.springframework.core.env.StandardEnvironment;

/**
 * Undoes what a profile's logging config leaves behind in a shared IT JVM. Spring Boot copies
 * logging settings into system properties (e.g. {@code CONSOLE_LOG_STRUCTURED_FORMAT}) and never
 * clears them, so one {@code ecs} run would make every later context log JSON.
 */
public final class LoggingUtils {

  private LoggingUtils() {}

  public static void restoreDefaults() {
    for (LoggingSystemProperty property : LoggingSystemProperty.values()) {
      System.clearProperty(property.getEnvironmentVariableName());
    }
    // Back to text logging; the last cleanUp() lets the next SpringApplication apply its own.
    LoggingSystem system = LoggingSystem.get(LoggingUtils.class.getClassLoader());
    system.cleanUp();
    system.beforeInitialize();
    system.initialize(new LoggingInitializationContext(new StandardEnvironment()), null, null);
    system.cleanUp();
    MDC.clear();
  }
}
