package com.example.lostfound.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.file.Path;

@Component
@Profile("prod")
public class ProductionConfigValidator {
    @Value("${app.public-api-base-url}")
    private String publicApiBaseUrl;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${wechat.appid}")
    private String wechatAppId;

    @Value("${wechat.secret}")
    private String wechatSecret;

    @Value("${media.upload-dir}")
    private String uploadDir;

    @Value("${ai.enabled:false}")
    private boolean aiEnabled;

    @Value("${zhipu.api-key:}")
    private String zhipuApiKey;

    @PostConstruct
    public void validate() {
        URI uri = URI.create(publicApiBaseUrl);
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                || "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
                || host.endsWith(".example.edu") || host.endsWith(".example")) {
            throw new IllegalStateException("生产 API 基址必须是真实的非 localhost HTTPS 地址");
        }
        if (!uri.getPath().endsWith("/api")) {
            throw new IllegalStateException("生产 API 基址必须以 /api 结尾");
        }
        if (uploadDir == null || !Path.of(uploadDir).isAbsolute()) {
            throw new IllegalStateException("生产图片目录必须是持久化私有卷的绝对路径");
        }
        if (jwtSecret == null || jwtSecret.length() < 32
                || jwtSecret.toLowerCase().contains("change")
                || jwtSecret.toLowerCase().contains("example")) {
            throw new IllegalStateException("生产 JWT_SECRET 必须是至少 32 字符的随机值");
        }
        if (wechatAppId == null || wechatAppId.isBlank()
                || wechatSecret == null || wechatSecret.isBlank()) {
            throw new IllegalStateException("生产微信凭据不能为空");
        }
        if (aiEnabled && (zhipuApiKey == null || zhipuApiKey.isBlank())) {
            throw new IllegalStateException("启用 AI 识图时必须配置 ZHIPU_API_KEY");
        }
    }
}
