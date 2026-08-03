package com.github.henc.test;

import com.github.henc.test.user.entity.User;
import com.github.henc.test.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test that boots the full application via {@code @IntegrateBoot} and verifies
 * the end-to-end wiring: conventional layer scanning, MyBatis-Flex mapper access,
 * transactional service, underscore-to-camelCase mapping, and auto-increment primary key
 * write-back.
 *
 * <p>Each test method is wrapped in a Spring test-managed transaction that rolls back at the
 * end, so seeding data is preserved across tests.
 */
@SpringBootTest
@Transactional
class UserServiceIT {

    @Autowired
    private UserService userService;

    @Test
    void findAllReturnsSeededUsers() {
        List<User> users = userService.findAll();
        assertThat(users).hasSize(2);
        assertThat(users).extracting(User::getUserName).contains("alice", "bob");
    }

    @Test
    void findByIdMapsSnakeCaseColumn() {
        User alice = userService.findById(1L);
        assertThat(alice).isNotNull();
        // user_name column maps to userName field — verifies the data-layer default.
        assertThat(alice.getUserName()).isEqualTo("alice");
        assertThat(alice.getAge()).isEqualTo(28);
    }

    @Test
    void createAssignsAutoIncrementId() {
        User user = new User("charlie", 40);

        User saved = userService.create(user);

        // The auto-incremented primary key must be written back into the entity.
        assertThat(saved.getId()).isNotNull().isPositive();
        // The id sequence continues past the two seeded rows.
        assertThat(saved.getId()).isGreaterThan(2L);

        // And the row is actually persisted and queryable by the generated id.
        User found = userService.findById(saved.getId());
        assertThat(found).isNotNull();
        assertThat(found.getUserName()).isEqualTo("charlie");
        assertThat(found.getAge()).isEqualTo(40);
    }
}
