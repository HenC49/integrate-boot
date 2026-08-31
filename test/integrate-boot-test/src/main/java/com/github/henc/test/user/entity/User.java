package com.github.henc.test.user.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

/**
 * Demo entity under the {@code entity} package. Fields are camelCase; the H2 table uses
 * snake_case columns, so this also verifies the underscore-to-camelCase mapping enabled by
 * integrate-boot-data.
 */
@Table("user")
public class User {

    @Id(keyType = KeyType.Auto)
    @Column("id")
    private Long id;
    @Column("user_name")
    private String userName;
    @Column("age")
    private Integer age;

    public User() {
    }

    public User(String userName, Integer age) {
        this.userName = userName;
        this.age = age;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    @Override
    public String toString() {
        return "User{id=" + id + ", userName='" + userName + '\'' + ", age=" + age + '}';
    }
}
