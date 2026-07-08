package com.example.lostfound.controller;

import com.example.lostfound.entity.Application;
import com.example.lostfound.entity.Item;
import com.example.lostfound.entity.User;
import com.example.lostfound.service.ApplicationService;
import com.example.lostfound.service.ItemService;
import com.example.lostfound.service.MessageService;
import com.example.lostfound.service.UserService;
import com.example.lostfound.util.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService applicationService;
    private final ItemService itemService;
    private final UserService userService;
    private final MessageService messageService;
    private final JwtUtil jwtUtil;

    public ApplicationController(ApplicationService applicationService,
                                 ItemService itemService,
                                 UserService userService,
                                 MessageService messageService,
                                 JwtUtil jwtUtil) {
        this.applicationService = applicationService;
        this.itemService = itemService;
        this.userService = userService;
        this.messageService = messageService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/item/{itemId}")
    public ResponseEntity<?> getApplicationsByItem(
            @RequestHeader("Authorization") String token,
            @PathVariable Long itemId) {

        Long userId = jwtUtil.getUserIdFromToken(jwtUtil.extractToken(token));
        Item item = itemService.findById(itemId);

        if (item == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "物品不存在");
            return ResponseEntity.status(404).body(error);
        }

        if (!item.getPublisherId().equals(userId)) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "无权查看");
            return ResponseEntity.status(403).body(error);
        }

        List<Application> applications = applicationService.findByItemId(itemId);
        return ResponseEntity.ok(applications);
    }

    @GetMapping("/my")
    public ResponseEntity<?> getMyApplications(
            @RequestHeader("Authorization") String token) {

        Long userId = jwtUtil.getUserIdFromToken(jwtUtil.extractToken(token));
        List<Application> applications = applicationService.findByApplicantId(userId);
        return ResponseEntity.ok(applications);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> applyAction(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, Object> request) {

        Long userId = jwtUtil.getUserIdFromToken(jwtUtil.extractToken(token));

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

        String type = (String) request.get("type");
        if (type == null || (!"help".equals(type) && !"claim".equals(type))) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "type必须为help或claim");
            return ResponseEntity.badRequest().body(error);
        }

        // 校验内容长度
        String content = (String) request.get("content");
        if (content == null || content.trim().isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "申请内容不能为空");
            return ResponseEntity.badRequest().body(error);
        }
        if (content.length() > 500) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "申请内容不能超过500字");
            return ResponseEntity.badRequest().body(error);
        }

        Item item = itemService.findById(itemId);
        if (item == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "物品不存在");
            return ResponseEntity.status(404).body(error);
        }

        // 不能申请已解决的物品
        if (!"active".equals(item.getStatus())) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "该物品已不再接受申请");
            return ResponseEntity.badRequest().body(error);
        }

        // 不能认领自己发布的物品
        if ("claim".equals(type) && item.getPublisherId().equals(userId)) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "不能认领自己发布的物品");
            return ResponseEntity.badRequest().body(error);
        }

        // 重复申请检测
        Application existing = applicationService.findByItemAndApplicant(itemId, userId, type);
        if (existing != null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "您已提交过相同的申请");
            return ResponseEntity.badRequest().body(error);
        }

        Application application = new Application();
        application.setItemId(itemId);
        application.setApplicantId(userId);
        application.setType(type);
        application.setContent(content);

        if (request.containsKey("images")) {
            application.setImages(request.get("images").toString());
        }

        application = applicationService.save(application);

        User applicant = userService.findById(userId);
        String applicantName = applicant.getNickname() != null ? applicant.getNickname() : "用户";

        if ("claim".equals(type)) {
            messageService.sendClaimApplyMessage(item.getPublisherId(), itemId, applicantName);
        } else {
            messageService.sendHelpOfferMessage(item.getPublisherId(), itemId, applicantName);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("application", application);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/handle")
    public ResponseEntity<Map<String, Object>> handleApplication(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {

        Long userId = jwtUtil.getUserIdFromToken(jwtUtil.extractToken(token));
        Application application = applicationService.findById(id);

        if (application == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "申请不存在");
            return ResponseEntity.status(404).body(error);
        }

        Item item = itemService.findById(application.getItemId());
        if (item == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "物品不存在");
            return ResponseEntity.status(404).body(error);
        }
        if (!item.getPublisherId().equals(userId)) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "无权处理此申请");
            return ResponseEntity.status(403).body(error);
        }

        // 不能重复处理
        if (!"pending".equals(application.getStatus())) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "该申请已被处理");
            return ResponseEntity.badRequest().body(error);
        }

        String action = request.get("action");
        if (action == null || (!"accept".equalsIgnoreCase(action) && !"reject".equalsIgnoreCase(action))) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "action必须为accept或reject");
            return ResponseEntity.badRequest().body(error);
        }

        if ("accept".equalsIgnoreCase(action)) {
            // 原子性操作：接受申请 + 拒绝其他 + 更新物品状态
            applicationService.acceptAndResolve(id, application.getItemId());

            // 通知被接受的申请人
            messageService.sendSystemNotice(application.getApplicantId(), "申请通过",
                    "您对\"" + item.getTitle() + "\"的申请已被通过");

            // 通知被自动拒绝的其他申请人
            List<Application> otherApps = applicationService.findByItemId(application.getItemId());
            for (Application other : otherApps) {
                if (!other.getId().equals(id) && "rejected".equals(other.getStatus())) {
                    messageService.sendSystemNotice(other.getApplicantId(), "申请已关闭",
                            "您对\"" + item.getTitle() + "\"的申请已被关闭（物品已找到失主/认领者）");
                }
            }
        } else {
            applicationService.handleApplication(id, "reject");
            messageService.sendSystemNotice(application.getApplicantId(), "申请被拒绝",
                    "您对\"" + item.getTitle() + "\"的申请已被拒绝");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "处理成功");

        return ResponseEntity.ok(response);
    }
}
