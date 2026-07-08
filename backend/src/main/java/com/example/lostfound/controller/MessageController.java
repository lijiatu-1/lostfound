package com.example.lostfound.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.lostfound.entity.Message;
import com.example.lostfound.service.MessageService;
import com.example.lostfound.util.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;
    private final JwtUtil jwtUtil;

    public MessageController(MessageService messageService, JwtUtil jwtUtil) {
        this.messageService = messageService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getMessages(
            @RequestHeader("Authorization") String token,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = jwtUtil.getUserIdFromToken(jwtUtil.extractToken(token));
        Page<Message> result = messageService.findByReceiverId(userId, page, pageSize);
        Map<String, Object> response = new HashMap<>();
        response.put("messages", result.getRecords());
        response.put("total", result.getTotal());
        response.put("page", result.getCurrent());
        response.put("pageSize", result.getSize());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/count")
    public ResponseEntity<Map<String, Object>> getUnreadCount(@RequestHeader("Authorization") String token) {
        Long userId = jwtUtil.getUserIdFromToken(jwtUtil.extractToken(token));
        Integer count = messageService.countUnread(userId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("count", count);
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Map<String, Object>> markAsRead(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id) {
        
        Long userId = jwtUtil.getUserIdFromToken(jwtUtil.extractToken(token));
        Message message = messageService.findById(id);

        if (message == null || !message.getReceiverId().equals(userId)) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "消息不存在");
            return ResponseEntity.status(404).body(error);
        }

        if (message.getIsRead() != null && message.getIsRead()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "消息已读");
            return ResponseEntity.ok(response);
        }

        messageService.markAsRead(id);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "已标记为已读");
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/read-all")
    public ResponseEntity<Map<String, Object>> markAllAsRead(@RequestHeader("Authorization") String token) {
        Long userId = jwtUtil.getUserIdFromToken(jwtUtil.extractToken(token));
        messageService.markAllAsRead(userId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "全部已标记为已读");
        
        return ResponseEntity.ok(response);
    }
}