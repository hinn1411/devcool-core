package com.devcool.adapters.in.web.dto.schema;

import com.devcool.adapters.in.web.dto.response.GetProfileResponse;
import com.devcool.adapters.in.web.dto.wrapper.ApiSuccessResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "GetProfileSuccess")
public class GetProfileSuccess extends ApiSuccessResponse<GetProfileResponse> {}
