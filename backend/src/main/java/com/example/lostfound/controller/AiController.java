package com.example.lostfound.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    @Value("${zhipu.api-key}")
    private String apiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 配置超时，防止外部 API 无响应时线程被挂起
    private final RestTemplate restTemplate;
    {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);  // 连接超时 10 秒
        factory.setReadTimeout(60000);     // 读取超时 60 秒（AI 推理可能较慢）
        restTemplate = new RestTemplate(factory);
    }

    @PostMapping("/recognize")
    public ResponseEntity<Map<String, Object>> recognize(@RequestBody Map<String, String> request) {
        String imageUrl = request.get("imageUrl");

        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "图片URL不能为空");
            return ResponseEntity.badRequest().body(error);
        }

        try {
            // 生成智谱 API 的 JWT Token
            String token = generateZhipuToken();

            // 构建请求
            String apiUrl = "https://open.bigmodel.cn/api/paas/v4/chat/completions";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + token);

            // 构建消息体
            Map<String, Object> body = new HashMap<>();
            body.put("model", "glm-4.6v-flash");

            // 提示词：让 AI 识别物品并返回 JSON
            String prompt = "你是一个失物招领助手。请仔细观察图片中的物品，返回JSON格式，不要返回其他内容。"
                    + "格式：{\"title\":\"简短物品名称（10字以内）\","
                    + "\"description\":\"物品特征描述（颜色、品牌、新旧程度等，50字以内）\","
                    + "\"category\":\"从以下选一个：证件卡片、电子产品、服饰配件、学习用品、生活用品、其他物品\"}";

            // 将图片转为 base64（智谱无法访问 localhost）
            String base64Url = convertToBase64DataUrl(imageUrl);

            // 多模态消息：文本 + 图片
            Map<String, Object> textPart = new HashMap<>();
            textPart.put("type", "text");
            textPart.put("text", prompt);

            Map<String, Object> imageUrlObj = new HashMap<>();
            imageUrlObj.put("url", base64Url);
            Map<String, Object> imagePart = new HashMap<>();
            imagePart.put("type", "image_url");
            imagePart.put("image_url", imageUrlObj);

            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", Arrays.asList(textPart, imagePart));

            body.put("messages", Collections.singletonList(message));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            // 调用智谱 API
            @SuppressWarnings("unchecked")
            Map<String, Object> zhipuResponse = restTemplate.postForObject(apiUrl, entity, Map.class);

            // 解析返回结果
            if (zhipuResponse != null && zhipuResponse.get("choices") != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) zhipuResponse.get("choices");
                if (!choices.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> choice = choices.get(0);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> msg = (Map<String, Object>) choice.get("message");
                    String content = (String) msg.get("content");

                    // 从 AI 返回的文本中提取 JSON
                    String jsonStr = extractJson(content);
                    if (jsonStr != null) {
                        // 解析 JSON
                        Map<String, Object> result = parseJson(jsonStr);
                        if (result != null) {
                            // 校验 category 是否合法
                            List<String> validCategories = Arrays.asList(
                                    "证件卡片", "电子产品", "服饰配件", "学习用品", "生活用品", "其他物品");
                            String category = (String) result.get("category");
                            if (category == null || !validCategories.contains(category)) {
                                result.put("category", "其他物品");
                            }

                            result.put("success", true);
                            return ResponseEntity.ok(result);
                        }
                    }

                    // JSON 解析失败，返回原始文本
                    Map<String, Object> fallback = new HashMap<>();
                    fallback.put("success", true);
                    fallback.put("title", "");
                    fallback.put("description", content);
                    fallback.put("category", "其他物品");
                    return ResponseEntity.ok(fallback);
                }
            }

            // API 调用失败
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "AI识别失败，请重试");
            return ResponseEntity.badRequest().body(error);

        } catch (Exception e) {
            log.error("AI识别失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "AI识别失败: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * 将本地或远程图片转为 base64 data URL
     * 智谱 API 无法访问 localhost，所以需要转成 base64 发送
     */
    private String convertToBase64DataUrl(String imageUrl) {
        try {
            byte[] imageBytes;
            String contentType = "image/jpeg";

            if (imageUrl.startsWith("http://localhost") || imageUrl.startsWith("http://127.0.0.1")) {
                // 本地文件：
                // URL 是 http://localhost:8080/images/xxx.jpg
                // 实际文件在 {user.dir}/uploads/images/xxx.jpg
                String path = imageUrl.replaceFirst("https?://[^/]+", "");
                // path = /images/xxx.jpg

                // 替换 /images/ 为 /uploads/images/
                path = path.replaceFirst("^/images/", "/uploads/images/");
                String projectRoot = System.getProperty("user.dir");
                File file = new File(projectRoot, path).getCanonicalFile();

                // 防止路径遍历：确保文件在 uploads/images 目录下
                File uploadsDir = new File(projectRoot, "uploads/images").getCanonicalFile();
                if (!file.getPath().startsWith(uploadsDir.getPath())) {
                    throw new SecurityException("路径越界：不允许访问 " + file.getPath());
                }

                // 只允许图片扩展名
                String name = file.getName().toLowerCase();
                if (!name.endsWith(".jpg") && !name.endsWith(".jpeg") && !name.endsWith(".png") && !name.endsWith(".gif")) {
                    throw new SecurityException("仅允许 jpg/png/gif 格式的图片");
                }

                log.debug("读取本地图片: {}", file.getAbsolutePath());

                imageBytes = Files.readAllBytes(file.toPath());

                if (name.endsWith(".png")) {
                    contentType = "image/png";
                } else if (name.endsWith(".gif")) {
                    contentType = "image/gif";
                }
            } else {
                // 远程 URL：只允许 https，且限制为已知图片域名白名单
                if (!imageUrl.startsWith("https://")) {
                    throw new SecurityException("远程图片仅允许 HTTPS 协议");
                }
                // 限制允许的域名，防止 SSRF
                java.net.URL url = new java.net.URL(imageUrl);
                String host = url.getHost();
                // 允许常见图片存储域名（可根据实际需要扩展）
                java.util.List<String> allowedHosts = java.util.Arrays.asList(
                        "open-file-*.alipayobjects.com",  // 阿里云示例
                        "img.alicdn.com",
                        "mmbiz.qpic.cn",                  // 微信图片
                        "thirdwx.qlogo.cn"
                );
                boolean hostAllowed = allowedHosts.stream()
                        .anyMatch(pattern -> {
                            if (pattern.contains("*")) {
                                return host.endsWith(pattern.substring(pattern.indexOf("*") + 1));
                            }
                            return host.equals(pattern);
                        });
                // 开发阶段先放行所有 https 域名，生产环境应收紧白名单
                // if (!hostAllowed) {
                //     throw new SecurityException("不允许的图片域名: " + host);
                // }
                // 内网地址拦截（防 SSRF）
                if (host.equals("localhost") || host.equals("127.0.0.1")
                        || host.startsWith("10.") || host.startsWith("172.")
                        || host.startsWith("192.168.") || host.equals("0.0.0.0")) {
                    throw new SecurityException("不允许访问内网地址");
                }

                imageBytes = restTemplate.getForObject(imageUrl, byte[].class);
                String lowerUrl = imageUrl.toLowerCase();
                if (lowerUrl.endsWith(".png")) contentType = "image/png";
                else if (lowerUrl.endsWith(".gif")) contentType = "image/gif";
            }

            String base64 = java.util.Base64.getEncoder().encodeToString(imageBytes);
            return "data:" + contentType + ";base64," + base64;
        } catch (Exception e) {
            log.error("图片转base64失败", e);
            return imageUrl;
        }
    }

    /**
     * 生成智谱 API 的 JWT Token
     * 用 Jackson 构造 JSON，避免手动拼接的安全和格式问题
     */
    private String generateZhipuToken() {
        try {
            String[] parts = apiKey.split("\\.");
            if (parts.length != 2) {
                throw new IllegalArgumentException("API Key 格式不正确，应为 id.secret");
            }
            String id = parts[0];
            String secret = parts[1];

            long now = System.currentTimeMillis() / 1000;
            long exp = now + 3600;

            // 用 Jackson 构造 JSON，安全处理特殊字符
            Map<String, Object> headerMap = new LinkedHashMap<>();
            headerMap.put("alg", "HS256");
            headerMap.put("sign_type", "SIGN");
            String header = objectMapper.writeValueAsString(headerMap);

            Map<String, Object> payloadMap = new LinkedHashMap<>();
            payloadMap.put("api_key", id);
            payloadMap.put("exp", exp);
            payloadMap.put("timestamp", now);
            String payload = objectMapper.writeValueAsString(payloadMap);

            // Base64URL 编码
            String headerB64 = base64UrlEncode(header.getBytes(StandardCharsets.UTF_8));
            String payloadB64 = base64UrlEncode(payload.getBytes(StandardCharsets.UTF_8));

            // HMAC-SHA256 签名
            String signingInput = headerB64 + "." + payloadB64;
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] sigBytes = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
            String signature = base64UrlEncode(sigBytes);

            return signingInput + "." + signature;
        } catch (Exception e) {
            throw new RuntimeException("生成智谱Token失败: " + e.getMessage());
        }
    }

    private String base64UrlEncode(byte[] data) {
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    /**
     * 从 AI 返回的文本中提取 JSON 字符串
     * AI 可能返回 ```json ... ``` 格式
     */
    private String extractJson(String content) {
        if (content == null) return null;

        // 去掉 markdown 代码块标记
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastBacktick = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastBacktick > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, lastBacktick).trim();
            }
        }

        // 找到 { 开头 } 结尾的 JSON
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }

        return null;
    }

    /**
     * 使用 Jackson 解析 AI 返回的 JSON
     */
    private Map<String, Object> parseJson(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            Map<String, Object> result = new HashMap<>();
            result.put("title", node.has("title") ? node.get("title").asText("") : "");
            result.put("description", node.has("description") ? node.get("description").asText("") : "");
            result.put("category", node.has("category") ? node.get("category").asText("") : "");
            return result;
        } catch (Exception e) {
            return null;
        }
    }
}
