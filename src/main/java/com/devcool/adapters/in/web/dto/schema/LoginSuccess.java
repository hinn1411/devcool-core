package com.devcool.adapters.in.web.dto.schema;

import com.devcool.adapters.in.web.dto.response.LoginResponse;
import com.devcool.adapters.in.web.dto.wrapper.ApiSuccessResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "LoginSuccess")
public class LoginSuccess extends ApiSuccessResponse<LoginResponse> {}
