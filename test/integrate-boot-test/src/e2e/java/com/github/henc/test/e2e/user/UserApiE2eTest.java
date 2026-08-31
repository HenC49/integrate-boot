package com.github.henc.test.e2e.user;

import com.github.henc.test.e2e.support.E2eTest;
import com.github.henc.test.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@E2eTest
class UserApiE2eTest {

    private static final ParameterizedTypeReference<List<User>> USER_LIST =
            new ParameterizedTypeReference<>() {
            };

    @Autowired
    private RestTestClient client;

    @Test
    void listReturnsSeededUsersOverHttp() {
        client.get().uri("/users")
                .exchange()
                .expectStatus().isOk()
                .expectBody(USER_LIST)
                .value(users -> assertThat(users).extracting(User::getUserName)
                        .contains("alice", "bob"));
    }

    @Test
    void getReturnsMappedUserOverHttp() {
        client.get().uri("/users/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody(User.class)
                .value(user -> assertThat(user.getUserName()).isEqualTo("alice"));
    }

    @Test
    void postCreatesUserAndReturnsGeneratedId() {
        client.post().uri("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new User("charlie", 40))
                .exchange()
                .expectStatus().isOk()
                .expectBody(User.class)
                .value(user -> assertThat(user.getId()).isNotNull().isPositive());
    }
}
