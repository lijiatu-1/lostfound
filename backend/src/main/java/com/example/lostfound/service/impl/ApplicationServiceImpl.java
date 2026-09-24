package com.example.lostfound.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.lostfound.entity.Application;
import com.example.lostfound.entity.Conversation;
import com.example.lostfound.entity.Item;
import com.example.lostfound.entity.User;
import com.example.lostfound.mapper.ApplicationMapper;
import com.example.lostfound.mapper.ItemMapper;
import com.example.lostfound.service.ApplicationService;
import com.example.lostfound.service.ConversationService;
import com.example.lostfound.service.MessageService;
import com.example.lostfound.service.UserService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ApplicationServiceImpl implements ApplicationService {
    private final ApplicationMapper applications;
    private final ItemMapper items;
    private final ConversationService conversations;
    private final MessageService notifications;
    private final UserService users;

    public ApplicationServiceImpl(ApplicationMapper applications, ItemMapper items,
                                  ConversationService conversations, MessageService notifications,
                                  UserService users) {
        this.applications = applications;
        this.items = items;
        this.conversations = conversations;
        this.notifications = notifications;
        this.users = users;
    }

    @Override public Application findById(Long id) { return applications.selectById(id); }
    @Override public List<Application> findByItemId(Long itemId) { return applications.findByItemId(itemId); }
    @Override public List<Application> findByApplicantId(Long id) { return applications.findByApplicantId(id); }
    @Override public Application findByItemAndApplicant(Long itemId, Long userId, String type) {
        return applications.findByItemAndApplicant(itemId, userId, type);
    }

    @Override
    @Transactional
    public Application save(Application application) {
        if (application.getId() == null) {
            application.setStatus("pending");
            application.setCreatedAt(LocalDateTime.now());
            applications.insert(application);
        } else {
            applications.updateById(application);
        }
        return application;
    }

    @Override
    @Transactional
    public Application submit(Long userId, Long itemId, String content) {
        if (itemId == null || itemId <= 0 || content == null || content.isBlank() || content.length() > 500) {
            throw new IllegalArgumentException("物品 ID 和 1 到 500 字申请内容均为必填");
        }
        if (!users.isAuthenticated(userId)) {
            throw new SecurityException("请先完成校园卡认证");
        }
        Item item = items.lockById(itemId);
        if (item == null) throw new IllegalArgumentException("物品不存在");
        if (!"active".equals(item.getStatus()) || item.getExpireAt() == null
                || !item.getExpireAt().isAfter(LocalDateTime.now())) {
            throw new IllegalStateException("该物品目前不接受申请");
        }
        if (userId.equals(item.getPublisherId())) {
            throw new IllegalArgumentException("不能申请自己发布的物品");
        }
        String type = "found".equals(item.getType()) ? "claim" : "help";
        Application application = new Application();
        application.setItemId(itemId);
        application.setApplicantId(userId);
        application.setType(type);
        application.setContent(content.trim());
        application.setStatus("pending");
        application.setCreatedAt(LocalDateTime.now());
        try {
            applications.insert(application);
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("您已提交过此物品的申请");
        }
        User applicant = users.findById(userId);
        String name = applicant != null && applicant.getNickname() != null ? applicant.getNickname() : "用户";
        if ("claim".equals(type)) notifications.sendClaimApplyMessage(item.getPublisherId(), itemId, name);
        else notifications.sendHelpOfferMessage(item.getPublisherId(), itemId, name);
        return application;
    }

    @Override
    @Transactional
    public Long handle(Long applicationId, Long publisherId, String action) {
        if (!"accept".equals(action) && !"reject".equals(action)) {
            throw new IllegalArgumentException("action 必须为 accept 或 reject");
        }
        Application first = applications.selectById(applicationId);
        if (first == null) throw new IllegalArgumentException("申请不存在");
        Item item = items.lockById(first.getItemId());
        if (item == null) throw new IllegalArgumentException("物品不存在");
        if (!publisherId.equals(item.getPublisherId())) throw new SecurityException("无权处理此申请");
        Application current = applications.selectById(applicationId);
        if (!"active".equals(item.getStatus()) || !"pending".equals(current.getStatus())) {
            throw new IllegalStateException("物品或申请状态已变化，请刷新后重试");
        }
        if ("reject".equals(action)) {
            current.setStatus("rejected");
            applications.updateById(current);
            notifications.sendSystemNotice(current.getApplicantId(), "申请被拒绝", "您对“" + item.getTitle() + "”的申请未通过");
            return null;
        }
        // The item row lock serializes approvals and new applications.
        item.setStatus("processing");
        items.updateById(item);
        current.setStatus("accepted");
        applications.updateById(current);
        List<Application> pending = applications.selectList(new QueryWrapper<Application>()
                .eq("item_id", item.getId()).eq("status", "pending"));
        for (Application other : pending) {
            other.setStatus("closed");
            applications.updateById(other);
            notifications.sendSystemNotice(other.getApplicantId(), "申请已关闭", "物品“" + item.getTitle() + "”已有处理中的申请");
        }
        Conversation conversation = conversations.createForAcceptedApplication(current, publisherId);
        notifications.sendSystemNotice(current.getApplicantId(), "申请通过", "您对“" + item.getTitle() + "”的申请已通过，可进入私信沟通");
        return conversation.getId();
    }

    @Override
    @Transactional
    public void closePendingByItemId(Long itemId) {
        Application update = new Application();
        update.setStatus("closed");
        applications.update(update, new QueryWrapper<Application>().eq("item_id", itemId).eq("status", "pending"));
    }

    @Override
    @Transactional
    public void deleteByItemId(Long itemId) {
        applications.delete(new QueryWrapper<Application>().eq("item_id", itemId));
    }
}
