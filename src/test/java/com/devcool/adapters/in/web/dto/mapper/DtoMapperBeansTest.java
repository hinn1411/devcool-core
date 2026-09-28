package com.devcool.adapters.in.web.dto.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * The controllers receive these mappers through constructor injection. No test in the suite starts
 * a Spring context, so this is the only thing that fails if a mapper stops being a discoverable
 * bean.
 */
class DtoMapperBeansTest {

  @Test
  void everyInboundDtoMapperIsExposedAsExactlyOneSpringBean() {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.scan("com.devcool.adapters.in.web.dto.mapper");
      context.refresh();

      assertThat(context.getBeansOfType(AuthDtoMapper.class)).hasSize(1);
      assertThat(context.getBeansOfType(ChannelDtoMapper.class)).hasSize(1);
      assertThat(context.getBeansOfType(MediaDtoMapper.class)).hasSize(1);
      assertThat(context.getBeansOfType(MessageDtoMapper.class)).hasSize(1);
    }
  }
}
