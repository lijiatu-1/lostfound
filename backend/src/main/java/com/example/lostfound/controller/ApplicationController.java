package com.example.lostfound.controller;

import com.example.lostfound.entity.Item;
import com.example.lostfound.service.ApplicationService;
import com.example.lostfound.service.ItemService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {
    private final ApplicationService applications;
    private final ItemService items;

    public ApplicationController(ApplicationService applications, ItemService items) {
        this.applications = applications;
        this.items = items;
    }

    @GetMapping("/item/{itemId}")
    public ResponseEntity<?> byItem(@RequestAttribute("userId") Long userId, @PathVariable Long itemId) {
        Item item = items.findById(itemId);
        if (item == null) return ResponseEntity.notFound().build();
        if (!userId.equals(item.getPublisherId())) throw new SecurityException("无权查看此物品申请");
        return ResponseEntity.ok(applications.findByItemId(itemId));
    }

    @GetMapping("/my")
    public ResponseEntity<?> mine(@RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(applications.findByApplicantId(userId));
    }

    @PostMapping
    public ResponseEntity<?> submit(@RequestAttribute("userId") Long userId,
                                    @RequestBody Map<String, Object> body) {
        if (!body.keySet().stream().allMatch(k -> "itemId".equals(k) || "content".equals(k))) {
            throw new IllegalArgumentException("仅接受 itemId 和 content");
        }
        Object rawId = body.get("itemId");
        Long itemId = rawId instanceof Number number ? number.longValue() : null;
        Object rawContent = body.get("content");
        String content = rawContent instanceof String text ? text : null;
        return ResponseEntity.ok(Map.of("success", true, "application",
                applications.submit(userId, itemId, content)));
    }

    @PostMapping("/{id}/handle")
    public ResponseEntity<?> handle(@RequestAttribute("userId") Long userId,
                                    @PathVariable Long id,
                                    @RequestBody Map<String, String> body) {
        Long conversationId = applications.handle(id, userId, body.get("action"));
        if (conversationId == null) return ResponseEntity.ok(Map.of("success", true, "message", "申请已拒绝"));
        return ResponseEntity.ok(Map.of("success", true, "message", "申请已批准", "conversationId", conversationId));
    }
}
