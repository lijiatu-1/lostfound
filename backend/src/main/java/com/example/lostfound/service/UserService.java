package com.example.lostfound.service;

import com.example.lostfound.entity.User;

public interface UserService {

    User findByOpenid(String openid);

    User findById(Long id);

    User save(User user);

    User update(User user);

    void updateStatus(Long id, String status);

    boolean isAuthenticated(Long userId);
}