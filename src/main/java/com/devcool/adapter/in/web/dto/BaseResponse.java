package com.devcool.adapter.in.web.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class BaseResponse {
    int resultCode;
    String description;
}