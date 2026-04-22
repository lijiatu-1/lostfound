package com.hbue.lostfound.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.hbue.lostfound.entity.User;
import com.hbue.lostfound.repository.UserRepository;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    // 注册用户
    public User register(String username, String password, String phone) {
        // 检查用户名是否已存在
        if (userRepository.findByUsername(username) != null) {
            throw new RuntimeException("用户名已存在");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(password);  // 实际项目要MD5加密
        user.setPhone(phone);

        return userRepository.save(user);
    }
}