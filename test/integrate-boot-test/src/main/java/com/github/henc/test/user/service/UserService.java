package com.github.henc.test.user.service;

import com.github.henc.test.user.entity.User;

import java.util.List;

/**
 * User service interface, placed under the {@code service} package for conventional scanning.
 */
public interface UserService {

    List<User> findAll();

    User findById(Long id);

    /**
     * Persist a new user. The auto-incremented primary key is written back into the
     * returned entity, so callers can read {@link User#getId()} afterwards.
     */
    User create(User user);
}
