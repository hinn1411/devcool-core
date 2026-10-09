package com.devcool.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devcool.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The ALB calls the health probes without a token (P1-T10). Only {@code health} is exposed, and it
 * never shows which components it checked.
 */
class HealthEndpointIT extends AbstractIntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired WebEndpointsSupplier webEndpoints;
  @Autowired ApplicationEventPublisher events;

  @ParameterizedTest
  @ValueSource(
      strings = {"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"})
  void healthEndpoints_areUpWithoutTokenAndHideDetails(String path) throws Exception {
    mvc.perform(get(path))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.components").doesNotExist());
  }

  @Test
  void onlyHealthIsExposedOverHttp() {
    assertThat(webEndpoints.getEndpoints())
        .extracting(endpoint -> endpoint.getEndpointId().toString())
        .containsExactly("health");
  }

  @ParameterizedTest
  @ValueSource(strings = {"/actuator", "/actuator/env", "/actuator/heapdump"})
  void otherActuatorPaths_requireToken(String path) throws Exception {
    mvc.perform(get(path)).andExpect(status().isUnauthorized());
  }

  @Test
  void readiness_returns503WhileRefusingTraffic() throws Exception {
    AvailabilityChangeEvent.publish(events, this, ReadinessState.REFUSING_TRAFFIC);
    try {
      mvc.perform(get("/actuator/health/readiness"))
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.status").value("OUT_OF_SERVICE"));
      // Liveness is a separate state: refusing traffic must not get the task restarted
      mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    } finally {
      // The context is cached and shared by every IT; leave it accepting traffic
      AvailabilityChangeEvent.publish(events, this, ReadinessState.ACCEPTING_TRAFFIC);
    }
  }
}
