package com.devcool.adapters.web.dto.schema;

import com.devcool.adapters.web.dto.response.GetProfileResponse;
import com.devcool.adapters.web.dto.wrapper.ApiSuccessResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "GetProfileSuccess")
public class GetProfileSuccess extends ApiSuccessResponse<GetProfileResponse> {
}
