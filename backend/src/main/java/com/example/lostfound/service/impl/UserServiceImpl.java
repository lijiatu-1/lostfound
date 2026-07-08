package com.example.lostfound.service.impl;

import com.example.lostfound.entity.User;
import com.example.lostfound.mapper.UserMapper;
import com.example.lostfound.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public User findByOpenid(String openid) {
        return userMapper.findByOpenid(openid);
    }

    @Override
    public User findById(Long id) {
        return userMapper.selectById(id);
    }

    @Override
    @Transactional
    public User save(User user) {
        LocalDateTime now = LocalDateTime.now();
        if (user.getId() == null) {
            user.setCreatedAt(now);
            if (user.getStatus() == null) {
                user.setStatus("unauthorized");
            }
            if (user.getRole() == null) {
                user.setRole("user");
            }
            userMapper.insert(user);
            // 插入后重新查询，确保 DB 默认值（如 role）已填入对象
            user = userMapper.selectById(user.getId());
        } else {
            user.setUpdatedAt(now);
            userMapper.updateById(user);
        }
        return user;
    }

    @Override
    @Transactional
    public User update(User user) {
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return user;
    }

    @Override
    @Transactional
    public void updateStatus(Long id, String status) {
        User user = userMapper.selectById(id);
        if (user != null) {
            user.setStatus(status);
            user.setUpdatedAt(LocalDateTime.now());
            userMapper.updateById(user);
        }
    }

    @Override
    public boolean isAuthenticated(Long userId) {
        User user = userMapper.selectById(userId);
        return user != null && "authorized".equals(user.getStatus());
    }
}