package com.devcool.adapters.web.dto.request;

import jakarta.validation.constraints.NotNull;

public record GetProfileRequest (@NotNull Integer id) { }
