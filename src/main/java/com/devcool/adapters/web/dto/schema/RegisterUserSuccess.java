package com.devcool.adapters.web.dto.schema;

import com.devcool.adapters.web.dto.response.RegisterUserResponse;
import com.devcool.adapters.web.dto.wrapper.ApiSuccessResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "RegisterUserSuccess")
public class RegisterUserSuccess extends ApiSuccessResponse<RegisterUserResponse> {}
