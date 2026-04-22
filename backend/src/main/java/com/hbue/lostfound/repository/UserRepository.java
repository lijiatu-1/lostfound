package com.hbue.lostfound.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.hbue.lostfound.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
    // 根据用户名查找用户（登录时用）
    User findByUsername(String username);
}