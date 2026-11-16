package com.shipmed.shipmed.domain.user.dto;

import com.shipmed.shipmed.domain.user.dto.response.UserDto;
import com.shipmed.shipmed.domain.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(source = "ship.id", target = "shipId")
    UserDto toDto(User user);
}
