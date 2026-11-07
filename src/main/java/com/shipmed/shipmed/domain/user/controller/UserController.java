package com.shipmed.shipmed.domain.user.controller;

import com.shipmed.shipmed.domain.user.dto.request.UserCreateRequest;
import com.shipmed.shipmed.domain.user.dto.response.UserDto;
import com.shipmed.shipmed.domain.user.service.UserService;
import com.shipmed.shipmed.global.request.PageQuery;
import com.shipmed.shipmed.global.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RequestMapping("/api/users")
@RestController
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<UserDto> createUser(@Valid @RequestBody UserCreateRequest userCreateRequest) {
        UserDto userDto = userService.create(userCreateRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(userDto);
    }

    @GetMapping
    public ResponseEntity<PageResponse<UserDto>> getAllUsers(@Valid @ModelAttribute PageQuery pageQuery) {
        return ResponseEntity.ok(userService.getAllUsers(pageQuery));
    }
}
