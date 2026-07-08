package com.example.lostfound.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.lostfound.entity.Item;
import com.example.lostfound.service.ApplicationService;
import com.example.lostfound.service.ItemService;
import com.example.lostfound.service.UserService;
import com.example.lostfound.util.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/items")
public class ItemController {

    private final ItemService itemService;
    private final UserService userService;
    private final ApplicationService applicationService;
    private final JwtUtil jwtUtil;

    public ItemController(ItemService itemService, UserService userService,
                          ApplicationService applicationService, JwtUtil jwtUtil) {
        this.itemService = itemService;
        this.userService = userService;
        this.applicationService = applicationService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getItems(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {

        page = Math.max(page, 1);
        pageSize = Math.min(Math.max(pageSize, 1), 100);

        Page<Item> result;
        if (keyword != null && !keyword.isEmpty()) {
            result = itemService.search(keyword, page, pageSize);
        } else {
            result = itemService.findActiveItems(type, category, page, pageSize);
        }

        // 列表不返回手机号
        List<Item> items = result.getRecords();
        items.forEach(i -> i.setPhone(null));

        Map<String, Object> response = new HashMap<>();
        response.put("items", items);
        response.put("total", result.getTotal());
        response.put("page", result.getCurrent());
        response.put("pageSize", result.getSize());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/categories")
    public ResponseEntity<List<String>> getCategories() {
        List<String> categories = itemService.getCategories();
        return ResponseEntity.ok(categories);
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchItems(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        page = Math.max(page, 1);
        pageSize = Math.min(Math.max(pageSize, 1), 100);
        Page<Item> result = itemService.search(keyword, page, pageSize);
        List<Item> items = result.getRecords();
        items.forEach(i -> i.setPhone(null));

        Map<String, Object> response = new HashMap<>();
        response.put("items", items);
        response.put("total", result.getTotal());
        response.put("page", result.getCurrent());
        response.put("pageSize", result.getSize());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my")
    public ResponseEntity<Map<String, Object>> getMyItems(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {

        Long userId = jwtUtil.getUserIdFromToken(jwtUtil.extractToken(token));
        Page<Item> result = itemService.findByPublisherId(userId, page, pageSize);

        Map<String, Object> response = new HashMap<>();
        response.put("items", result.getRecords());
        response.put("total", result.getTotal());
        response.put("page", result.getCurrent());
        response.put("pageSize", result.getSize());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getItemDetail(@PathVariable Long id) {
        Item item = itemService.findById(id);
        if (item == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "物品不存在");
            return ResponseEntity.status(404).body(error);
        }
        // 隐藏手机号，需通过 /contact 接口单独获取
        item.setPhone(null);
        return ResponseEntity.ok(item);
    }

    // 单独获取联系方式（需登录+认证，且必须是物品发布者或已关联的申请者）
    @GetMapping("/{id}/contact")
    public ResponseEntity<Map<String, Object>> getContact(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id) {

        Long userId = jwtUtil.getUserIdFromToken(jwtUtil.extractToken(token));

        // 校验是否已认证
        if (!userService.isAuthenticated(userId)) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "请先完成校园卡认证");
            return ResponseEntity.status(403).body(error);
        }

        Item item = itemService.findById(id);

        if (item == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "物品不存在");
            return ResponseEntity.status(404).body(error);
        }

        // 权限校验：只有物品发布者或已关联该物品的申请者才能查看联系方式
        boolean isOwner = item.getPublisherId().equals(userId);
        boolean isApplicant = applicationService.findByApplicantId(userId).stream()
                .anyMatch(app -> app.getItemId().equals(id));
        if (!isOwner && !isApplicant) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "您未参与此物品，无法查看联系方式");
            return ResponseEntity.status(403).body(error);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("phone", item.getPhone());
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> publishItem(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, Object> request) {

        Long userId = jwtUtil.getUserIdFromToken(jwtUtil.extractToken(token));

        if (!userService.isAuthenticated(userId)) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "请先完成认证");
            return ResponseEntity.badRequest().body(error);
        }

        // 校验 type
        String type = (String) request.get("type");
        if (type == null || (!"lost".equals(type) && !"found".equals(type))) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "type必须为lost或found");
            return ResponseEntity.badRequest().body(error);
        }

        // 校验 title
        String title = (String) request.get("title");
        if (title == null || title.trim().isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "标题不能为空");
            return ResponseEntity.badRequest().body(error);
        }
        if (title.length() > 100) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "标题不能超过100字");
            return ResponseEntity.badRequest().body(error);
        }

        // 校验 description
        String description = (String) request.get("description");
        if (description == null || description.trim().isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "描述不能为空");
            return ResponseEntity.badRequest().body(error);
        }
        if (description.length() > 500) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "描述不能超过500字");
            return ResponseEntity.badRequest().body(error);
        }

        Item item = new Item();
        item.setPublisherId(userId);
        item.setType(type);

        // 处理分类
        String category = (String) request.get("category");
        List<String> validCategories = itemService.getCategories();
        if (category != null && validCategories.contains(category)) {
            item.setCategory(category);
        } else {
            item.setCategory("其他物品");
        }

        item.setTitle(title.trim());
        item.setDescription(description.trim());
        item.setLocationName((String) request.get("locationName"));
        
        if (request.containsKey("locationLat") && request.containsKey("locationLng")) {
            item.setLocationLat(((Number) request.get("locationLat")).doubleValue());
            item.setLocationLng(((Number) request.get("locationLng")).doubleValue());
        }
        
        if (request.containsKey("images")) {
            item.setImages(request.get("images").toString());
        }
        if (request.containsKey("tags")) {
            item.setTags(request.get("tags").toString());
        }

        // 处理联系电话
        if (request.containsKey("phone")) {
            String phone = (String) request.get("phone");
            if (phone != null && !phone.trim().isEmpty()) {
                item.setPhone(phone.trim());
            }
        }

        item = itemService.save(item);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("item", item);
        
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> editItem(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        
        Long userId = jwtUtil.getUserIdFromToken(jwtUtil.extractToken(token));
        Item item = itemService.findById(id);
        
        if (item == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "物品不存在");
            return ResponseEntity.status(404).body(error);
        }

        if (!item.getPublisherId().equals(userId)) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "无权修改此物品");
            return ResponseEntity.status(403).body(error);
        }

        if (!"active".equals(item.getStatus())) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "已过期或已解决的物品不能修改");
            return ResponseEntity.badRequest().body(error);
        }
        
        if (request.containsKey("title")) {
            String title = (String) request.get("title");
            if (title != null && title.length() > 100) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "标题不能超过100字");
                return ResponseEntity.badRequest().body(error);
            }
            item.setTitle(title);
        }
        if (request.containsKey("category")) {
            String cat = (String) request.get("category");
            List<String> validCategories = itemService.getCategories();
            if (validCategories.contains(cat)) {
                item.setCategory(cat);
            }
        }
        if (request.containsKey("description")) {
            String desc = (String) request.get("description");
            if (desc != null && desc.length() > 500) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "描述不能超过500字");
                return ResponseEntity.badRequest().body(error);
            }
            item.setDescription(desc);
        }
        if (request.containsKey("locationName")) {
            item.setLocationName((String) request.get("locationName"));
        }
        if (request.containsKey("locationLat") && request.containsKey("locationLng")) {
            item.setLocationLat(((Number) request.get("locationLat")).doubleValue());
            item.setLocationLng(((Number) request.get("locationLng")).doubleValue());
        }
        
        item = itemService.update(item);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("item", item);
        
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteItem(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id) {
        
        Long userId = jwtUtil.getUserIdFromToken(jwtUtil.extractToken(token));
        Item item = itemService.findById(id);
        
        if (item == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "物品不存在");
            return ResponseEntity.status(404).body(error);
        }

        if (!item.getPublisherId().equals(userId)) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "无权删除此物品");
            return ResponseEntity.status(403).body(error);
        }
        
        applicationService.deleteByItemId(id);
        itemService.delete(id);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "删除成功");
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<Map<String, Object>> resolveItem(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id) {
        
        Long userId = jwtUtil.getUserIdFromToken(jwtUtil.extractToken(token));
        Item item = itemService.findById(id);
        
        if (item == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "物品不存在");
            return ResponseEntity.status(404).body(error);
        }

        if (!item.getPublisherId().equals(userId)) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "无权操作此物品");
            return ResponseEntity.status(403).body(error);
        }

        itemService.updateStatus(id, "resolved");
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "已标记为已解决");
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/renew")
    public ResponseEntity<Map<String, Object>> renewItem(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id) {

        Long userId = jwtUtil.getUserIdFromToken(jwtUtil.extractToken(token));
        Item item = itemService.findById(id);

        if (item == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "物品不存在");
            return ResponseEntity.status(404).body(error);
        }

        if (!item.getPublisherId().equals(userId)) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "无权操作此物品");
            return ResponseEntity.status(403).body(error);
        }

        // 已解决的物品不能再延期（防止已解决物品被复活）
        if ("resolved".equals(item.getStatus())) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "已解决的物品不能延期");
            return ResponseEntity.badRequest().body(error);
        }

        // 过期或即将过期（3天内）的物品可以延期
        if ("active".equals(item.getStatus())) {
            // active 状态下，检查是否在3天内过期
            if (item.getExpireAt() == null || item.getExpireAt().isAfter(java.time.LocalDateTime.now().plusDays(3))) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "物品离过期还有较长时间，暂不支持延期");
                return ResponseEntity.badRequest().body(error);
            }
        }

        item.setStatus("active");
        item.setExpireAt(java.time.LocalDateTime.now().plusDays(7));
        itemService.update(item);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "已延期7天");
        response.put("item", item);

        return ResponseEntity.ok(response);
    }
}