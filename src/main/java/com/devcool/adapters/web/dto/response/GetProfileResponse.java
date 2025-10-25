package com.devcool.adapters.web.dto.response;

import com.devcool.domain.user.model.User;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class GetProfileResponse {
    private User user;
}
