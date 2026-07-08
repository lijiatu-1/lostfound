package com.example.lostfound.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.lostfound.entity.Message;
import com.example.lostfound.mapper.MessageMapper;
import com.example.lostfound.service.MessageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MessageServiceImpl implements MessageService {

    private final MessageMapper messageMapper;

    public MessageServiceImpl(MessageMapper messageMapper) {
        this.messageMapper = messageMapper;
    }

    @Override
    public Message findById(Long id) {
        return messageMapper.selectById(id);
    }

    @Override
    public Page<Message> findByReceiverId(Long receiverId, int page, int pageSize) {
        Page<Message> p = new Page<>(page, pageSize);
        QueryWrapper<Message> wrapper = new QueryWrapper<>();
        wrapper.eq("receiver_id", receiverId)
               .orderByDesc("created_at");
        return messageMapper.selectPage(p, wrapper);
    }

    @Override
    @Transactional
    public Message save(Message message) {
        if (message.getId() == null) {
            message.setCreatedAt(LocalDateTime.now());
            message.setIsRead(false);
            messageMapper.insert(message);
        } else {
            messageMapper.updateById(message);
        }
        return message;
    }

    @Override
    @Transactional
    public void markAsRead(Long id) {
        messageMapper.markAsRead(id);
    }

    @Override
    @Transactional
    public void markAllAsRead(Long receiverId) {
        messageMapper.markAllAsRead(receiverId);
    }

    @Override
    public Integer countUnread(Long receiverId) {
        return messageMapper.countUnread(receiverId);
    }

    @Override
    @Transactional
    public void sendClaimApplyMessage(Long receiverId, Long itemId, String applicantName) {
        Message message = new Message();
        message.setReceiverId(receiverId);
        message.setType("claim_apply");
        message.setTitle(applicantName + "申请认领您的物品");
        message.setContent("用户" + applicantName + "申请认领您发布的物品，请及时处理");
        message.setRelatedItemId(itemId);
        save(message);
    }

    @Override
    @Transactional
    public void sendHelpOfferMessage(Long receiverId, Long itemId, String helperName) {
        Message message = new Message();
        message.setReceiverId(receiverId);
        message.setType("help_offer");
        message.setTitle(helperName + "提供了帮助");
        message.setContent("用户" + helperName + "为您发布的物品提供了帮助信息，请查看");
        message.setRelatedItemId(itemId);
        save(message);
    }

    @Override
    @Transactional
    public void sendSystemNotice(Long receiverId, String title, String content) {
        Message message = new Message();
        message.setReceiverId(receiverId);
        message.setType("system_notice");
        message.setTitle(title);
        message.setContent(content);
        save(message);
    }
}