package com.devcool.adapters.in.web.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;


@Schema(name = "GetChannelResponse", description = "Get channels response")
@Getter
@Setter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GetChannelResponse {
  @Schema(description = "returned channels", example = "[]")
  private List<ChannelListItemResponse> channels;
  private Integer nextCursorId;
  boolean hasMore;
}
