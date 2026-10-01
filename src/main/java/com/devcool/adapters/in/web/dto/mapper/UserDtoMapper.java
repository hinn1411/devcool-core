package com.devcool.adapters.in.web.dto.mapper;

import com.devcool.adapters.in.web.dto.response.UserProfileResponse;
import com.devcool.domain.user.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserDtoMapper {

  UserProfileResponse toProfileResponse(User user);
}
