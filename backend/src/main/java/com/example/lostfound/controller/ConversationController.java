package com.example.lostfound.controller;

import com.example.lostfound.service.ConversationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {
    private final ConversationService conversations;

    public ConversationController(ConversationService conversations) {
        this.conversations = conversations;
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(Map.of("conversations", conversations.listForUser(userId)));
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<?> messages(@RequestAttribute("userId") Long userId,
                                      @PathVariable Long id,
                                      @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(Map.of(
                "conversation", conversations.requireParticipant(id, userId),
                "messages", conversations.listMessages(id, userId, limit)));
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<?> send(@RequestAttribute("userId") Long userId,
                                  @PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(Map.of("success", true,
                "message", conversations.send(id, userId, body.get("content"))));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<?> read(@RequestAttribute("userId") Long userId, @PathVariable Long id) {
        conversations.markRead(id, userId);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
