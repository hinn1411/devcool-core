package com.devcool.adapters.in.web.dto.response;


import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Schema(name = "UpdateChannelResponse", description = "Update channel response payload")
@Getter
@Setter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpdateChannelResponse {
  @Schema(description = "update status", example = "true/false")
  private boolean channelUpdated;
}
