package com.example.lostfound.controller;

import com.example.lostfound.entity.MediaAsset;
import com.example.lostfound.service.AiUsageService;
import com.example.lostfound.service.MediaAssetService;
import com.example.lostfound.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Only a user's own managed item image may be transmitted to the fixed provider endpoint. */
@RestController
@RequestMapping("/api/ai")
public class AiController {
    private static final String ENDPOINT = "https://open.bigmodel.cn/api/paas/v4/chat/completions";
    private static final Set<String> CATEGORIES = Set.of(
            "证件卡片", "电子产品", "服饰配件", "学习用品", "生活用品", "其他物品");
    private static final String PROMPT = "你是校园失物招领助手。只描述图片中可见的物品，不推断个人身份，"
            + "不要抄录姓名、学号、电话、证件号码或二维码内容。"
            + "只返回 JSON，字段为 title（10字内名称）、description（50字内可见特征）、"
            + "category（证件卡片/电子产品/服饰配件/学习用品/生活用品/其他物品之一）。";

    private final MediaAssetService assets;
    private final AiUsageService usage;
    private final UserService users;
    private final ObjectMapper mapper;
    private final RestTemplate restTemplate;
    private final String apiKey;
    private final boolean enabled;
    private final long maxBytes;
    private final long maxPixels;

    public AiController(MediaAssetService assets, AiUsageService usage, UserService users,
                        ObjectMapper mapper,
                        @Value("${zhipu.api-key:}") String apiKey,
                        @Value("${ai.enabled:false}") boolean enabled,
                        @Value("${media.max-upload-bytes:5242880}") long maxBytes,
                        @Value("${media.max-pixels:16000000}") long maxPixels) {
        this.assets = assets;
        this.usage = usage;
        this.users = users;
        this.mapper = mapper;
        this.apiKey = apiKey;
        this.enabled = enabled;
        this.maxBytes = maxBytes;
        this.maxPixels = maxPixels;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(20000);
        this.restTemplate = new RestTemplate(factory);
    }

    @PostMapping("/recognize")
    public ResponseEntity<?> recognize(@RequestAttribute("userId") Long userId,
                                       @RequestBody Map<String, Object> body) {
        if (body.size() != 1 || !(body.get("assetId") instanceof Number || body.get("assetId") instanceof String)) {
            throw new IllegalArgumentException("仅接受 assetId");
        }
        Long assetId;
        try {
            assetId = Long.valueOf(body.get("assetId").toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("assetId 无效");
        }
        if (assetId <= 0) throw new IllegalArgumentException("assetId 无效");
        if (!users.isAuthenticated(userId)) throw new SecurityException("请先完成校园卡认证");
        MediaAsset asset = assets.requireOwnedAsset(userId, assetId, MediaAssetService.PURPOSE_ITEM);
        if (asset.getSizeBytes() == null || asset.getSizeBytes() <= 0 || asset.getSizeBytes() > maxBytes
                || asset.getWidth() == null || asset.getHeight() == null
                || asset.getWidth() <= 0 || asset.getHeight() <= 0
                || (long) asset.getWidth() * asset.getHeight() > maxPixels) {
            throw new IllegalArgumentException("图片超出识别限制");
        }
        if (!enabled || apiKey == null || apiKey.isBlank()) {
            return failure(HttpStatus.SERVICE_UNAVAILABLE, "AI 识图暂未配置，请手动填写");
        }

        byte[] image;
        try {
            image = assets.readContent(asset);
        } catch (IOException e) {
            return failure(HttpStatus.NOT_FOUND, "图片文件不存在");
        }
        if (image.length == 0 || image.length > maxBytes) {
            throw new IllegalArgumentException("图片超出识别限制");
        }
        if (!usage.tryConsume(userId)) {
            return failure(HttpStatus.TOO_MANY_REQUESTS, "今日 AI 识图次数已用完，请明天再试");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            String dataUrl = "data:" + asset.getContentType() + ";base64,"
                    + Base64.getEncoder().encodeToString(image);
            Map<String, Object> text = Map.of("type", "text", "text", PROMPT);
            Map<String, Object> picture = Map.of("type", "image_url", "image_url", Map.of("url", dataUrl));
            Map<String, Object> request = Map.of(
                    "model", "glm-4.6v-flash",
                    "messages", List.of(Map.of("role", "user", "content", List.of(text, picture))));
            ResponseEntity<String> response = restTemplate.postForEntity(
                    ENDPOINT, new HttpEntity<>(request, headers), String.class);
            return ResponseEntity.ok(parseResult(response.getBody()));
        } catch (Exception e) {
            // Do not log or return provider payloads: they may contain image data or credentials.
            return failure(HttpStatus.BAD_GATEWAY, "AI 识图失败，请稍后重试或手动填写");
        }
    }

    private Map<String, Object> parseResult(String response) throws IOException {
        JsonNode choices = mapper.readTree(response).path("choices");
        if (!choices.isArray() || choices.isEmpty()) throw new IOException("没有识图结果");
        String content = choices.get(0).path("message").path("content").asText("");
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) throw new IOException("识图结果格式错误");
        JsonNode result = mapper.readTree(content.substring(start, end + 1));
        String title = clipped(result.path("title").asText(""), 10);
        String description = clipped(result.path("description").asText(""), 50);
        String category = result.path("category").asText("");
        if (title.isBlank() || description.isBlank()) throw new IOException("识图结果为空");
        return Map.of("success", true, "title", title, "description", description,
                "category", CATEGORIES.contains(category) ? category : "其他物品");
    }

    private String clipped(String value, int maxLength) {
        String clean = value.replaceAll("[\\p{Cntrl}]", " ")
                .replaceAll("[0-9]{6,}", "[号码已隐藏]").trim();
        return clean.length() > maxLength ? clean.substring(0, maxLength) : clean;
    }

    private ResponseEntity<Map<String, Object>> failure(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("success", false, "message", message));
    }
}
