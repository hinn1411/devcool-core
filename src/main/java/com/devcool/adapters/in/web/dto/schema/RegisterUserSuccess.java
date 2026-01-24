package com.devcool.adapters.in.web.dto.schema;

import com.devcool.adapters.in.web.dto.response.RegisterUserResponse;
import com.devcool.adapters.in.web.dto.wrapper.ApiSuccessResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "RegisterUserSuccess")
public class RegisterUserSuccess extends ApiSuccessResponse<RegisterUserResponse> {}
