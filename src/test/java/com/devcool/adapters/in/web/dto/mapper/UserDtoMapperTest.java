package com.devcool.adapters.in.web.dto.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.devcool.adapters.in.web.dto.response.UserProfileResponse;
import com.devcool.domain.user.model.User;
import com.devcool.domain.user.model.enums.Role;
import com.devcool.domain.user.model.enums.UserStatus;
import org.junit.jupiter.api.Test;

class UserDtoMapperTest {

  private final UserDtoMapper mapper = new UserDtoMapperImpl();

  @Test
  void toProfileResponse_mapsThePublicFields() {
    User user =
        User.builder()
            .id(101)
            .username("hien_giang")
            .password("stored-hash")
            .email("hien@example.com")
            .name("Hien Giang")
            .avatar("https://cdn.devcool.com/avatars/hien.png")
            .role(Role.USER)
            .status(UserStatus.ACTIVE)
            .tokenVersion(7)
            .build();

    UserProfileResponse response = mapper.toProfileResponse(user);

    assertThat(response.getId()).isEqualTo("101");
    assertThat(response.getUsername()).isEqualTo("hien_giang");
    assertThat(response.getName()).isEqualTo("Hien Giang");
    assertThat(response.getAvatar()).isEqualTo("https://cdn.devcool.com/avatars/hien.png");
  }

  @Test
  void toProfileResponse_userWithoutId_leavesIdNull() {
    User user = User.builder().username("hien_giang").build();

    assertThat(mapper.toProfileResponse(user).getId()).isNull();
  }
}
