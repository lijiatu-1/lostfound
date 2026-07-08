package com.example.lostfound.controller;

import com.example.lostfound.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ImageController {

    @Value("${app.upload.dir:uploads/images}")
    private String uploadDir;

    private final JwtUtil jwtUtil;

    // 允许的图片扩展名白名单
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    public ImageController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    private String getAbsolutePath() {
        if (new java.io.File(uploadDir).isAbsolute()) {
            return uploadDir;
        }
        return System.getProperty("user.dir") + "/" + uploadDir;
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadImage(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam("file") MultipartFile file) {

        // 认证校验：必须登录才能上传
        Long userId;
        try {
            String token = jwtUtil.extractToken(authHeader);
            userId = jwtUtil.getUserIdFromToken(token);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "请先登录");
            return ResponseEntity.status(401).body(error);
        }

        Map<String, Object> response = new HashMap<>();

        if (file.isEmpty()) {
            response.put("success", false);
            response.put("message", "文件不能为空");
            return ResponseEntity.badRequest().body(response);
        }

        long maxSize = 5 * 1024 * 1024; // 5MB
        if (file.getSize() > maxSize) {
            response.put("success", false);
            response.put("message", "文件不能超过5MB");
            return ResponseEntity.badRequest().body(response);
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            response.put("success", false);
            response.put("message", "只允许上传图片文件");
            return ResponseEntity.badRequest().body(response);
        }

        // 从 Content-Type 提取扩展名，但必须通过白名单校验
        String extension = contentType.replace("image/", "").toLowerCase();
        if ("jpeg".equals(extension)) extension = "jpg";
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            response.put("success", false);
            response.put("message", "不支持的图片格式，仅允许 jpg/png/gif/webp");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            String filename = UUID.randomUUID() + "." + extension;

            Path dir = Paths.get(getAbsolutePath());
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }

            Path filePath = dir.resolve(filename);
            file.transferTo(filePath.toFile());

            String url = "/images/" + filename;
            response.put("success", true);
            response.put("url", url);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "文件保存失败");
            return ResponseEntity.status(500).body(response);
        }
    }
}
