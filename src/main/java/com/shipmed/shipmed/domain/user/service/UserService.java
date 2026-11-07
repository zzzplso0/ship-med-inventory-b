package com.shipmed.shipmed.domain.user.service;

import com.shipmed.shipmed.domain.user.dto.request.UserCreateRequest;
import com.shipmed.shipmed.domain.user.dto.response.UserDto;
import com.shipmed.shipmed.global.request.PageQuery;
import com.shipmed.shipmed.global.response.PageResponse;

public interface UserService {

    UserDto create(UserCreateRequest userCreateRequest);
    PageResponse<UserDto> getAllUsers(PageQuery pageQuery);
}
