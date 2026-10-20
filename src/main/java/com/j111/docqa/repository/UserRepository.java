package com.j111.docqa.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.j111.docqa.entity.User;

/**
 * 用户数据访问。继承 JpaRepository 后基本的增删改查和分页都由 Spring Data 生成实现。
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);
}
