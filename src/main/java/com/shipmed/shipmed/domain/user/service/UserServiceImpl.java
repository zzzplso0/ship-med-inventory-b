package com.shipmed.shipmed.domain.user.service;

import com.shipmed.shipmed.domain.ship.entity.Ship;
import com.shipmed.shipmed.domain.ship.exception.ShipNotFoundException;
import com.shipmed.shipmed.domain.ship.repository.ShipRepository;
import com.shipmed.shipmed.domain.user.dto.UserMapper;
import com.shipmed.shipmed.domain.user.dto.request.UserCreateRequest;
import com.shipmed.shipmed.domain.user.dto.response.UserDto;
import com.shipmed.shipmed.domain.user.entity.User;
import com.shipmed.shipmed.domain.user.exception.UserDuplicateException;
import com.shipmed.shipmed.domain.user.repository.UserRepository;
import com.shipmed.shipmed.global.request.PageQuery;
import com.shipmed.shipmed.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class UserServiceImpl implements UserService {

    private static final String UQ_USER_LOGIN = "uq_user_login";

    private final UserRepository userRepository;
    private final ShipRepository shipRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    @Override
    public UserDto create(UserCreateRequest userCreateRequest) {
        Ship ship = null;
        if (userCreateRequest.shipId() != null) {
            ship = shipRepository.findById(userCreateRequest.shipId())
                    .orElseThrow(ShipNotFoundException::new);
        }

        boolean existsUser = userRepository.existsByLoginId(userCreateRequest.loginId());

        if (existsUser) {
            throw new UserDuplicateException();
        }

        User user = new User(
                userCreateRequest.loginId(),
                userCreateRequest.name(),
                passwordEncoder.encode(userCreateRequest.password()),
                userCreateRequest.role(),
                ship
        );

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // 동시 요청이 중복 확인을 함께 통과한 경우. 다른 제약 위반은 409로 바꾸지 않는다.
            if (ex.getCause() instanceof ConstraintViolationException cause
                    && UQ_USER_LOGIN.equals(cause.getConstraintName())) {
                throw new UserDuplicateException();
            }
            throw ex;
        }

        return userMapper.toDto(user);
    }

    @Override
    public PageResponse<UserDto> getAllUsers(PageQuery pageQuery) {
        Page<User> users = userRepository.findAll(pageQuery.toPageable());

        return PageResponse.from(users.map(userMapper::toDto));
    }
}
