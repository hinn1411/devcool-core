package com.devcool.adapters.web.dto.response;

import com.devcool.domain.user.model.enums.Role;
import com.devcool.domain.user.model.enums.UserStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Builder
public class RegisterUserResponse {
    Integer id;
    String username;
    String email;
    String name;
    String avatar;
    Role role;
    UserStatus status;
}