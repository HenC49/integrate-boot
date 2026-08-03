package com.github.henc.test.user.service.impl;

import com.github.henc.test.user.domain.UserRepository;
import com.github.henc.test.user.entity.User;
import com.github.henc.test.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * User service implementation under {@code service.impl}. {@code @Transactional} is honoured
 * by MyBatis-Flex's {@code FlexTransactionManager} auto-configured by the data layer.
 */
@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userRepository.selectAll();
    }

    @Override
    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.selectOneById(id);
    }

    @Override
    @Transactional
    public User create(User user) {
        // insert() returns the number of affected rows; the auto-generated id is written
        // back into the entity by MyBatis-Flex.
        userRepository.insert(user);
        return user;
    }
}
