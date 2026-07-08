package com.example.lostfound.controller;

import com.example.lostfound.entity.Certification;
import com.example.lostfound.entity.User;
import com.example.lostfound.service.CertificationService;
import com.example.lostfound.service.MessageService;
import com.example.lostfound.service.UserService;
import com.example.lostfound.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;
    private final MessageService messageService;
    private final CertificationService certificationService;
    private final JwtUtil jwtUtil;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${wechat.appid}")
    private String wechatAppid;

    @Value("${wechat.secret}")
    private String wechatSecret;

    public AuthController(UserService userService, MessageService messageService,
                          CertificationService certificationService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.messageService = messageService;
        this.certificationService = certificationService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> request) {
        String code = request.get("code");
        if (code == null || code.trim().isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "登录失败，code不能为空");
            return ResponseEntity.badRequest().body(error);
        }

        // 用 code 换取 openid
        String openid = exchangeCodeForOpenid(code);
        if (openid == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "登录失败，无法获取用户身份");
            return ResponseEntity.badRequest().body(error);
        }

        User user = userService.findByOpenid(openid);
        if (user == null) {
            user = new User();
            user.setOpenid(openid);
            user.setNickname("用户" + System.currentTimeMillis());
            user = userService.save(user);
        }

        // 检查用户是否被封禁（预留机制，目前 status 不含 banned，但提前做好防御）
        if ("banned".equals(user.getStatus())) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "该账号已被封禁");
            return ResponseEntity.status(403).body(error);
        }

        String token = jwtUtil.generateToken(user.getId());
        Integer unreadCount = messageService.countUnread(user.getId());

        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("user", user);
        response.put("unreadMessageCount", unreadCount);

        return ResponseEntity.ok(response);
    }

    /**
     * 调用微信 jscode2session 接口，用 code 换取 openid
     * 开发模式：仅当 spring.profiles.active=dev 时，允许 mock_ 前缀的 code
     */
    @Value("${spring.profiles.active:}")
    private String activeProfile;

    private String exchangeCodeForOpenid(String code) {
        // 开发模式：仅 dev 环境允许 mock_ 前缀的 code，防止生产环境被冒充
        if (code.startsWith("mock_") && "dev".equals(activeProfile)) {
            System.out.println("[开发模式] 使用 mock openid: " + code);
            return code;
        }

        // 如果 secret 没配置，仅在 dev 环境下返回 mock openid
        if ("your_app_secret_here".equals(wechatSecret) && "dev".equals(activeProfile)) {
            System.out.println("[开发模式] 微信 AppSecret 未配置，使用 mock openid");
            return "mock_openid_" + code;
        }

        try {
            String url = "https://api.weixin.qq.com/sns/jscode2session"
                    + "?appid=" + wechatAppid
                    + "&secret=" + wechatSecret
                    + "&js_code=" + code
                    + "&grant_type=authorization_code";

            String response = restTemplate.getForObject(url, String.class);

            // 微信返回的是 text/plain，需手动解析
            if (response != null) {
                // 简单解析 JSON：{"openid":"xxx","session_key":"yyy"}
                int openidIdx = response.indexOf("\"openid\"");
                if (openidIdx >= 0) {
                    int colonIdx = response.indexOf(':', openidIdx);
                    int quoteStart = response.indexOf('"', colonIdx + 1);
                    int quoteEnd = response.indexOf('"', quoteStart + 1);
                    if (quoteStart >= 0 && quoteEnd > quoteStart) {
                        return response.substring(quoteStart + 1, quoteEnd);
                    }
                }
                // 检查是否有错误
                if (response.contains("\"errcode\"")) {
                    System.err.println("微信登录失败: " + response);
                }
            }
            return null;
        } catch (Exception e) {
            log.error("调用微信接口失败", e);
            return null;
        }
    }

    @GetMapping("/user")
    public ResponseEntity<Map<String, Object>> getUser(
            @RequestHeader("Authorization") String token) {
        try {
            Long userId = jwtUtil.getUserIdFromToken(jwtUtil.extractToken(token));
            User user = userService.findById(userId);
            if (user == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "用户不存在");
                return ResponseEntity.status(404).body(error);
            }
            Map<String, Object> response = new HashMap<>();
            response.put("id", user.getId());
            response.put("nickname", user.getNickname());
            response.put("avatarUrl", user.getAvatarUrl());
            response.put("status", user.getStatus());
            response.put("role", user.getRole());
            response.put("realName", user.getRealName());
            response.put("studentId", user.getStudentId());
            response.put("createdAt", user.getCreatedAt());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取用户信息失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Token无效");
            return ResponseEntity.status(401).body(error);
        }
    }

    @PostMapping("/certification")
    public ResponseEntity<Map<String, Object>> submitCertification(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, String> request) {
        try {
            Long userId = jwtUtil.getUserIdFromToken(jwtUtil.extractToken(token));

            String realName = request.get("realName");
            String studentId = request.get("studentId");
            String cardPhoto = request.get("cardPhoto");

            if (realName == null || realName.trim().isEmpty() ||
                studentId == null || studentId.trim().isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "姓名和学号不能为空");
                return ResponseEntity.badRequest().body(error);
            }

            // 检查是否已有认证记录
            java.util.List<Certification> existingList = certificationService.findByUserId(userId);
            Certification existing = (existingList != null && !existingList.isEmpty()) ? existingList.get(0) : null;
            if (existing != null && "pending".equals(existing.getStatus())) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "您已有待审核的认证申请");
                return ResponseEntity.badRequest().body(error);
            }

            Certification cert;
            if (existing != null) {
                // 已有记录（rejected），更新而非插入新记录，避免 UNIQUE 约束冲突
                cert = existing;
                cert.setRealName(realName.trim());
                cert.setStudentId(studentId.trim());
                cert.setCardPhoto(cardPhoto);
                cert.setStatus("pending");
                cert.setReviewerId(null);
                cert.setReviewMsg(null);
                certificationService.update(cert);
            } else {
                cert = new Certification();
                cert.setUserId(userId);
                cert.setRealName(realName.trim());
                cert.setStudentId(studentId.trim());
                cert.setCardPhoto(cardPhoto);
                cert.setStatus("pending");
                certificationService.save(cert);
            }

            // 更新用户信息
            User user = userService.findById(userId);
            user.setRealName(realName.trim());
            user.setStudentId(studentId.trim());
            userService.update(user);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "认证申请已提交，请等待审核");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("提交认证失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "提交失败");
            return ResponseEntity.status(500).body(error);
        }
    }

    @PostMapping("/certification/{id}/review")
    public ResponseEntity<Map<String, Object>> reviewCertification(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        try {
            Long reviewerId = jwtUtil.getUserIdFromToken(jwtUtil.extractToken(token));
            User reviewer = userService.findById(reviewerId);

            if (reviewer == null || !"admin".equals(reviewer.getRole())) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "仅管理员可审核认证");
                return ResponseEntity.status(403).body(error);
            }

            Certification cert = certificationService.findById(id);
            if (cert == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "认证记录不存在");
                return ResponseEntity.status(404).body(error);
            }

            String action = request.get("action");
            String reviewMsg = request.get("reviewMsg");

            // 校验 action 必须是 accept 或 reject
            if (!"accept".equals(action) && !"reject".equals(action)) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "action必须为accept或reject");
                return ResponseEntity.badRequest().body(error);
            }

            if ("accept".equals(action)) {
                cert.setStatus("authorized");
                cert.setReviewerId(reviewerId);
                cert.setReviewMsg(reviewMsg);
                certificationService.update(cert);

                // 更新用户状态
                User user = userService.findById(cert.getUserId());
                user.setStatus("authorized");
                userService.update(user);

                // 发送系统通知
                messageService.sendSystemNotice(cert.getUserId(), "认证通过通知",
                        "恭喜！你的校园卡认证已通过，现在可以发布失物信息了。");
            } else if ("reject".equals(action)) {
                cert.setStatus("rejected");
                cert.setReviewerId(reviewerId);
                cert.setReviewMsg(reviewMsg);
                certificationService.update(cert);

                // 认证被拒绝时，回滚用户 PII 信息（防止未认证用户携带已认证样信息）
                User user = userService.findById(cert.getUserId());
                if (user != null) {
                    user.setRealName(null);
                    user.setStudentId(null);
                    userService.update(user);
                }

                messageService.sendSystemNotice(cert.getUserId(), "认证未通过",
                        "很遗憾，你的校园卡认证未通过。原因：" + (reviewMsg != null ? reviewMsg : "信息有误"));
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "审核完成");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("审核认证失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "审核失败");
            return ResponseEntity.status(500).body(error);
        }
    }

    @GetMapping("/certifications/pending")
    public ResponseEntity<Map<String, Object>> getPendingCertifications(
            @RequestHeader("Authorization") String token) {
        try {
            Long userId = jwtUtil.getUserIdFromToken(jwtUtil.extractToken(token));
            User user = userService.findById(userId);

            if (user == null || !"admin".equals(user.getRole())) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "仅管理员可查看");
                return ResponseEntity.status(403).body(error);
            }

            java.util.List<Certification> certs = certificationService.findPending();
            Map<String, Object> response = new HashMap<>();
            response.put("certifications", certs);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("查询待审核认证失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "查询失败");
            return ResponseEntity.status(500).body(error);
        }
    }

    @PostMapping("/profile")
    public ResponseEntity<Map<String, Object>> updateProfile(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, String> request) {
        try {
            Long userId = jwtUtil.getUserIdFromToken(jwtUtil.extractToken(token));
            User user = userService.findById(userId);

            if (user == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "用户不存在");
                return ResponseEntity.status(404).body(error);
            }

            if (request.containsKey("nickname")) {
                String nickname = request.get("nickname");
                if (nickname != null && !nickname.trim().isEmpty()) {
                    user.setNickname(nickname.trim());
                }
            }
            if (request.containsKey("avatarUrl")) {
                user.setAvatarUrl(request.get("avatarUrl"));
            }

            userService.update(user);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "修改成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("修改用户资料失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "修改失败");
            return ResponseEntity.status(500).body(error);
        }
    }
}
