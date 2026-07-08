package com.example.lostfound.config;

import com.example.lostfound.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * JWT 认证拦截器
 * 统一校验 Authorization 请求头中的 JWT token
 * 不需要认证的路径在 WebConfig 中排除
 */
@Component
public class JwtAuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 不需要认证的路径前缀
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/login",
            "/api/items",
            "/api/items/categories",
            "/api/items/search",
            "/api/ai/recognize",
            "/api/comments/item"
    );

    public JwtAuthInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // OPTIONS 预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();

        // 检查是否是公开路径
        // GET /api/items/{id} 是公开的，但 PUT/DELETE 需要认证
        if (isPublicPath(path, request.getMethod())) {
            return true;
        }

        // 从 header 提取 token
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || authHeader.trim().isEmpty()) {
            sendError(response, 401, "缺少Authorization请求头");
            return false;
        }

        try {
            String token = jwtUtil.extractToken(authHeader);
            Long userId = jwtUtil.getUserIdFromToken(token);
            // 将解析出的 userId 存入 request attribute，Controller 可直接获取
            request.setAttribute("userId", userId);
            return true;
        } catch (IllegalArgumentException e) {
            sendError(response, 401, e.getMessage());
            return false;
        }
    }

    private boolean isPublicPath(String path, String method) {
        // 登录接口公开
        if (path.equals("/api/auth/login")) return true;

        // GET /api/items 和 /api/items/{id} 公开（列表和详情）
        if (path.startsWith("/api/items") && "GET".equals(method)) return true;

        // 分类和搜索公开
        if (path.startsWith("/api/items/categories")) return true;
        if (path.startsWith("/api/items/search")) return true;

        // 评论查看公开
        if (path.startsWith("/api/comments/item") && "GET".equals(method)) return true;

        // AI 识别暂不公开（需要认证才能使用）
        // 上传需要认证

        return false;
    }

    private void sendError(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("message", message);
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
