package com.github.henc.test.user.domain;

import com.github.henc.test.user.entity.User;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Repository for {@link User}, placed under the {@code domain} package so the
 * {@code @IntegrateBoot} conventional scan picks it up.
 */
@Mapper
public interface UserRepository extends BaseMapper<User> {
}
