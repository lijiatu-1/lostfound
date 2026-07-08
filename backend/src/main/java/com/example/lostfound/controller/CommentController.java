package com.example.lostfound.controller;

import com.example.lostfound.entity.Comment;
import com.example.lostfound.entity.Item;
import com.example.lostfound.entity.User;
import com.example.lostfound.service.CommentService;
import com.example.lostfound.service.ItemService;
import com.example.lostfound.service.UserService;
import com.example.lostfound.util.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/comments")
public class CommentController {

    private final CommentService commentService;
    private final ItemService itemService;
    private final UserService userService;
    private final JwtUtil jwtUtil;

    public CommentController(CommentService commentService, ItemService itemService,
                             UserService userService, JwtUtil jwtUtil) {
        this.commentService = commentService;
        this.itemService = itemService;
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    // 获取某物品的所有评论（附带用户昵称）
    @GetMapping("/item/{itemId}")
    public ResponseEntity<List<Map<String, Object>>> getCommentsByItem(@PathVariable Long itemId) {
        List<Comment> comments = commentService.findByItemId(itemId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Comment comment : comments) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", comment.getId());
            map.put("itemId", comment.getItemId());
            map.put("userId", comment.getUserId());
            map.put("content", comment.getContent());
            map.put("createdAt", comment.getCreatedAt());
            // 查询用户昵称
            User user = userService.findById(comment.getUserId());
            map.put("nickname", user != null ? user.getNickname() : "匿名用户");
            result.add(map);
        }
        return ResponseEntity.ok(result);
    }

    // 发表评论
    @PostMapping
    public ResponseEntity<Map<String, Object>> createComment(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, Object> request) {

        Long userId = jwtUtil.getUserIdFromToken(jwtUtil.extractToken(token));

        // 必须是认证用户才能评论
        if (!userService.isAuthenticated(userId)) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "请先完成认证");
            return ResponseEntity.badRequest().body(error);
        }

        // 校验 itemId
        if (request.get("itemId") == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "itemId不能为空");
            return ResponseEntity.badRequest().body(error);
        }
        Long itemId = ((Number) request.get("itemId")).longValue();

        // 校验 content
        String content = (String) request.get("content");
        if (content == null || content.trim().isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "评论内容不能为空");
            return ResponseEntity.badRequest().body(error);
        }
        if (content.length() > 500) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "评论内容不能超过500字");
            return ResponseEntity.badRequest().body(error);
        }

        // 只允许对 "lost" 类型的物品评论
        Item item = itemService.findById(itemId);
        if (item == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "物品不存在");
            return ResponseEntity.status(404).body(error);
        }
        if (!"lost".equals(item.getType())) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "只有寻物信息可以评论");
            return ResponseEntity.badRequest().body(error);
        }

        // 只允许对 active 状态的帖子评论
        if (!"active".equals(item.getStatus())) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "该帖子已过期或已解决，不能评论");
            return ResponseEntity.badRequest().body(error);
        }

        // 发布者不能评论自己的帖子
        if (item.getPublisherId().equals(userId)) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "不能评论自己的帖子");
            return ResponseEntity.badRequest().body(error);
        }

        // 5分钟内不能重复评论
        if (commentService.hasRecentDuplicate(itemId, userId, content.trim())) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "请勿重复评论");
            return ResponseEntity.badRequest().body(error);
        }

        Comment comment = new Comment();
        comment.setItemId(itemId);
        comment.setUserId(userId);
        comment.setContent(content.trim());

        comment = commentService.save(comment);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("comment", comment);
        return ResponseEntity.ok(response);
    }

    // 删除评论（本人或帖子发布者可删）
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteComment(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id) {

        Long userId = jwtUtil.getUserIdFromToken(jwtUtil.extractToken(token));
        Comment comment = commentService.findById(id);

        if (comment == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "评论不存在");
            return ResponseEntity.status(404).body(error);
        }

        // 只有评论者本人或帖子发布者可以删除
        Item item = itemService.findById(comment.getItemId());
        if (item == null) {
            // 物品已删除，评论者本人仍可删除自己的评论
            if (!comment.getUserId().equals(userId)) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "无权删除此评论");
                return ResponseEntity.status(403).body(error);
            }
        } else if (!comment.getUserId().equals(userId) && !item.getPublisherId().equals(userId)) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "无权删除此评论");
            return ResponseEntity.status(403).body(error);
        }

        commentService.deleteById(id);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "删除成功");
        return ResponseEntity.ok(response);
    }
}
