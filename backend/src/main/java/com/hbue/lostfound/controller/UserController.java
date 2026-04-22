package com.hbue.lostfound.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.hbue.lostfound.entity.User;
import com.hbue.lostfound.service.UserService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    // 注册接口 POST /api/user/register
    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody User user) {
        Map<String, Object> result = new HashMap<>();

        try {
            User savedUser = userService.register(
                    user.getUsername(),
                    user.getPassword(),
                    user.getPhone()
            );

            result.put("code", 200);
            result.put("message", "注册成功");
            result.put("data", savedUser);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
        }

        return result;
    }

    // 测试接口 GET /api/user/test
    @GetMapping("/test")
    public String test() {
        return "用户模块运行正常！";
    }
}