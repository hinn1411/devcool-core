package com.devcool.adapters.web.controller;

import com.devcool.adapters.web.dto.wrapper.ApiSuccessResponse;
import com.devcool.adapters.web.util.ApiResponseFactory;
import com.devcool.domain.common.ErrorCode;
import com.devcool.domain.user.model.User;
import com.devcool.domain.user.port.in.GetUserQuery;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
  private final GetUserQuery getUser;

  public UserController(GetUserQuery getUser) {
    this.getUser = getUser;
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiSuccessResponse<User>> getProfile(@PathVariable Integer id) {

    User user = getUser.byId(id);
    return ResponseEntity.ok(
        ApiResponseFactory.success(
            HttpStatus.OK, ErrorCode.OK.code(), "Get profile successfully", user));
  }
}
