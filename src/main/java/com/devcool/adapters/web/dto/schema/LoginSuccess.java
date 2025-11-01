package com.devcool.adapters.web.dto.schema;

import com.devcool.adapters.web.dto.response.LoginResponse;
import com.devcool.adapters.web.dto.wrapper.ApiSuccessResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "LoginSuccess")
public class LoginSuccess extends ApiSuccessResponse<LoginResponse> {}