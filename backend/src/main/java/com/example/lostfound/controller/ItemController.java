package com.example.lostfound.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.lostfound.entity.Item;
import com.example.lostfound.mapper.ItemMapper;
import com.example.lostfound.service.ApplicationService;
import com.example.lostfound.service.ConversationService;
import com.example.lostfound.service.ItemService;
import com.example.lostfound.service.MediaAssetService;
import com.example.lostfound.service.UserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/items")
public class ItemController {
    private static final Pattern PUBLIC_PHONE = Pattern.compile("(?<![0-9])1[3-9](?:[\\s-]?[0-9]){9}(?![0-9])");
    private static final Set<String> PUBLISH_FIELDS = Set.of(
            "type", "title", "description", "locationName", "category", "imageAssetIds", "tags");
    private static final Set<String> EDIT_FIELDS = Set.of(
            "title", "description", "locationName", "category", "imageAssetIds", "tags");
    private final ItemService items;
    private final ItemMapper itemMapper;
    private final UserService users;
    private final ApplicationService applications;
    private final ConversationService conversations;
    private final MediaAssetService mediaAssets;
    private final ObjectMapper json;

    public ItemController(ItemService items, ItemMapper itemMapper, UserService users,
                          ApplicationService applications, ConversationService conversations,
                          MediaAssetService mediaAssets, ObjectMapper json) {
        this.items = items;
        this.itemMapper = itemMapper;
        this.users = users;
        this.applications = applications;
        this.conversations = conversations;
        this.mediaAssets = mediaAssets;
        this.json = json;
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(required = false) String type,
                                  @RequestParam(required = false) String category,
                                  @RequestParam(required = false) String keyword,
                                  @RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "20") int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        Page<Item> result = keyword == null || keyword.isBlank()
                ? items.findActiveItems(type, category, safePage, safeSize)
                : items.search(keyword, safePage, safeSize);
        return ResponseEntity.ok(Map.of("items", result.getRecords().stream().map(this::redactPublicText).toList(), "total", result.getTotal(),
                "page", result.getCurrent(), "pageSize", result.getSize()));
    }

    @GetMapping("/categories")
    public ResponseEntity<?> categories() { return ResponseEntity.ok(items.getCategories()); }

    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam String keyword,
                                    @RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int pageSize) {
        Page<Item> result = items.search(keyword, Math.max(page, 1), Math.min(Math.max(pageSize, 1), 100));
        return ResponseEntity.ok(Map.of("items", result.getRecords().stream().map(this::redactPublicText).toList(), "total", result.getTotal(),
                "page", result.getCurrent(), "pageSize", result.getSize()));
    }

    @GetMapping("/my")
    public ResponseEntity<?> mine(@RequestAttribute("userId") Long userId,
                                  @RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "20") int pageSize) {
        Page<Item> result = items.findByPublisherId(userId, Math.max(page, 1), Math.min(Math.max(pageSize, 1), 100));
        return ResponseEntity.ok(Map.of("items", result.getRecords().stream().map(this::redactPublicText).toList(), "total", result.getTotal(),
                "page", result.getCurrent(), "pageSize", result.getSize()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable Long id) {
        Item item = items.findById(id);
        return item == null || "deleted".equals(item.getStatus())
                ? ResponseEntity.notFound().build() : ResponseEntity.ok(redactPublicText(item));
    }

    @PostMapping
    public ResponseEntity<?> publish(@RequestAttribute("userId") Long userId,
                                     @RequestBody Map<String, Object> body) {
        if (!users.isAuthenticated(userId)) throw new SecurityException("请先完成校园卡认证");
        rejectLegacySensitiveFields(body);
        rejectUnknownFields(body, PUBLISH_FIELDS);
        String type = text(body, "type", 10);
        if (!"lost".equals(type) && !"found".equals(type)) throw new IllegalArgumentException("type 必须为 lost 或 found");
        Item item = new Item();
        item.setPublisherId(userId);
        item.setType(type);
        applyEditableFields(item, body, userId, true);
        items.save(item);
        return ResponseEntity.ok(Map.of("success", true, "item", item));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> edit(@RequestAttribute("userId") Long userId, @PathVariable Long id,
                                  @RequestBody Map<String, Object> body) {
        rejectLegacySensitiveFields(body);
        rejectUnknownFields(body, EDIT_FIELDS);
        Item item = itemMapper.lockById(id);
        requireOwner(item, userId);
        if (!"active".equals(item.getStatus())) throw new IllegalStateException("只有公示中的物品可编辑");
        applyEditableFields(item, body, userId, false);
        items.update(item);
        return ResponseEntity.ok(Map.of("success", true, "item", item));
    }

    @PostMapping("/{id}/resolve")
    @Transactional
    public ResponseEntity<?> resolve(@RequestAttribute("userId") Long userId, @PathVariable Long id) {
        Item item = itemMapper.lockById(id);
        requireOwner(item, userId);
        if (!"active".equals(item.getStatus()) && !"processing".equals(item.getStatus())) {
            throw new IllegalStateException("当前状态不能结案");
        }
        item.setStatus("resolved");
        items.update(item);
        applications.closePendingByItemId(id);
        conversations.closeByItemId(id);
        return ResponseEntity.ok(Map.of("success", true, "item", item));
    }

    @PostMapping("/{id}/reopen")
    @Transactional
    public ResponseEntity<?> reopen(@RequestAttribute("userId") Long userId, @PathVariable Long id) {
        Item item = itemMapper.lockById(id);
        requireOwner(item, userId);
        if (!"processing".equals(item.getStatus())) throw new IllegalStateException("只有处理中的物品可以重新开放");
        conversations.closeByItemId(id);
        item.setStatus("active");
        if (item.getExpireAt() == null || !item.getExpireAt().isAfter(LocalDateTime.now())) {
            item.setExpireAt(LocalDateTime.now().plusDays(7));
        }
        items.update(item);
        return ResponseEntity.ok(Map.of("success", true, "item", item));
    }

    @PostMapping("/{id}/renew")
    @Transactional
    public ResponseEntity<?> renew(@RequestAttribute("userId") Long userId, @PathVariable Long id) {
        Item item = itemMapper.lockById(id);
        requireOwner(item, userId);
        if (!"expired".equals(item.getStatus()) && !"active".equals(item.getStatus())) {
            throw new IllegalStateException("只有公示中或过期的物品可以延期");
        }
        if ("active".equals(item.getStatus()) && item.getExpireAt() != null
                && item.getExpireAt().isAfter(LocalDateTime.now().plusDays(3))) {
            throw new IllegalStateException("离到期尚有三天以上");
        }
        item.setStatus("active");
        item.setExpireAt(LocalDateTime.now().plusDays(7));
        items.update(item);
        return ResponseEntity.ok(Map.of("success", true, "item", item));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> delete(@RequestAttribute("userId") Long userId, @PathVariable Long id) {
        Item item = itemMapper.lockById(id);
        requireOwner(item, userId);
        if ("deleted".equals(item.getStatus())) throw new IllegalStateException("物品已删除");
        item.setStatus("deleted");
        items.update(item);
        applications.closePendingByItemId(id);
        conversations.closeByItemId(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    private void requireOwner(Item item, Long userId) {
        if (item == null) throw new IllegalArgumentException("物品不存在");
        if (!userId.equals(item.getPublisherId())) throw new SecurityException("无权操作此物品");
    }

    private void rejectLegacySensitiveFields(Map<String, Object> body) {
        if (body.containsKey("phone") || body.containsKey("contact")
                || body.containsKey("locationLat") || body.containsKey("locationLng")
                || body.containsKey("latitude") || body.containsKey("longitude")
                || body.containsKey("images")) {
            throw new IllegalArgumentException("仅接受文字地点和受控图片资产 ID");
        }
    }

    private void rejectUnknownFields(Map<String, Object> body, Set<String> allowed) {
        if (!allowed.containsAll(body.keySet())) {
            throw new IllegalArgumentException("请求包含不支持的字段，只能使用受控图片资产 ID");
        }
    }

    private void applyEditableFields(Item item, Map<String, Object> body, Long userId, boolean create) {
        if (create || body.containsKey("title")) item.setTitle(requiredText(body, "title", 100));
        if (create || body.containsKey("description")) item.setDescription(requiredText(body, "description", 500));
        if (create || body.containsKey("locationName")) item.setLocationName(requiredText(body, "locationName", 255));
        if (create || body.containsKey("category")) {
            String category = text(body, "category", 50);
            item.setCategory(items.getCategories().contains(category) ? category : "其他物品");
        }
        if (create || body.containsKey("imageAssetIds")) {
            item.setImages(mediaAssets.normalizeItemAssetIds(userId, assetIds(body.get("imageAssetIds"))));
        }
        if (create || body.containsKey("tags")) {
            Object raw = body.get("tags");
            if (raw == null && create) raw = List.of();
            if (!(raw instanceof List<?> list) || list.size() > 10) {
                throw new IllegalArgumentException("tags 必须是不超过 10 项的字符串数组");
            }
            List<String> tags = new ArrayList<>();
            for (Object tag : list) {
                if (!(tag instanceof String text) || text.isBlank() || text.length() > 30) {
                    throw new IllegalArgumentException("标签需为 1 到 30 字");
                }
                rejectPublicPhone(text);
                tags.add(text.trim());
            }
            try { item.setTags(json.writeValueAsString(tags)); }
            catch (JsonProcessingException e) { throw new IllegalStateException("标签保存失败", e); }
        }
    }

    private List<Long> assetIds(Object raw) {
        if (raw == null) return List.of();
        if (!(raw instanceof List<?> values)) throw new IllegalArgumentException("imageAssetIds 必须是数组");
        List<Long> result = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof Number number) result.add(number.longValue());
            else if (value instanceof String text && text.matches("[0-9]+")) result.add(Long.valueOf(text));
            else throw new IllegalArgumentException("图片资产 ID 无效");
        }
        return result;
    }

    private String requiredText(Map<String, Object> body, String key, int max) {
        String value = text(body, key, max);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(key + " 不能为空");
        return value;
    }

    private String text(Map<String, Object> body, String key, int max) {
        Object raw = body.get(key);
        if (raw == null) return null;
        if (!(raw instanceof String value)) throw new IllegalArgumentException(key + " 必须是文本");
        String trimmed = value.trim();
        if (trimmed.length() > max) throw new IllegalArgumentException(key + " 过长");
        rejectPublicPhone(trimmed);
        return trimmed;
    }

    private void rejectPublicPhone(String value) {
        if (PUBLIC_PHONE.matcher(value).find()) {
            throw new IllegalArgumentException("公开物品信息请勿填写电话号码，请通过申请和私信联系");
        }
    }

    private Item redactPublicText(Item item) {
        Item view = new Item();
        BeanUtils.copyProperties(item, view);
        view.setTitle(maskPhone(view.getTitle()));
        view.setDescription(maskPhone(view.getDescription()));
        view.setLocationName(maskPhone(view.getLocationName()));
        view.setTags(maskPhone(view.getTags()));
        return view;
    }

    private String maskPhone(String value) {
        return value == null ? null : PUBLIC_PHONE.matcher(value).replaceAll("[已隐藏联系方式]");
    }
}
