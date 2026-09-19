package com.devcool.adapters.in.web.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Schema(name = "GetMessagesResponse", description = "Get messages response")
@Getter
@Setter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GetMessagesResponse {
  private List<MessageItemResponse> items;
  private Integer cursorId;
  private boolean hasMore;
}
