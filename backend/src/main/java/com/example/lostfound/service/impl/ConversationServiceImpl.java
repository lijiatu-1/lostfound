package com.example.lostfound.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.lostfound.entity.Application;
import com.example.lostfound.entity.ChatMessage;
import com.example.lostfound.entity.Conversation;
import com.example.lostfound.entity.Item;
import com.example.lostfound.mapper.ChatMessageMapper;
import com.example.lostfound.mapper.ConversationMapper;
import com.example.lostfound.mapper.ItemMapper;
import com.example.lostfound.service.ConversationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
public class ConversationServiceImpl implements ConversationService {
    private final ConversationMapper conversations;
    private final ChatMessageMapper messages;
    private final ItemMapper items;

    public ConversationServiceImpl(ConversationMapper conversations, ChatMessageMapper messages, ItemMapper items) {
        this.conversations = conversations;
        this.messages = messages;
        this.items = items;
    }

    @Override
    public Conversation createForAcceptedApplication(Application application, Long publisherId) {
        Conversation conversation = new Conversation();
        conversation.setItemId(application.getItemId());
        conversation.setApplicationId(application.getId());
        conversation.setPublisherId(publisherId);
        conversation.setApplicantId(application.getApplicantId());
        conversation.setStatus("open");
        conversation.setPublisherReadId(0L);
        conversation.setApplicantReadId(0L);
        conversation.setCreatedAt(LocalDateTime.now());
        conversations.insert(conversation);
        return conversation;
    }

    @Override
    public List<Conversation> listForUser(Long userId) {
        List<Conversation> result = conversations.selectList(new QueryWrapper<Conversation>()
                .and(w -> w.eq("publisher_id", userId).or().eq("applicant_id", userId))
                .orderByDesc("created_at"));
        result.forEach(this::addItemTitle);
        return result;
    }

    @Override
    public Conversation requireParticipant(Long conversationId, Long userId) {
        Conversation conversation = conversations.selectById(conversationId);
        if (conversation == null || (!userId.equals(conversation.getPublisherId())
                && !userId.equals(conversation.getApplicantId()))) {
            throw new SecurityException("会话不存在或无权访问");
        }
        addItemTitle(conversation);
        return conversation;
    }

    @Override
    public List<ChatMessage> listMessages(Long conversationId, Long userId, int limit) {
        requireParticipant(conversationId, userId);
        List<ChatMessage> result = messages.recent(conversationId, Math.min(Math.max(limit, 1), 100));
        Collections.reverse(result);
        return result;
    }

    @Override
    @Transactional
    public ChatMessage send(Long conversationId, Long userId, String content) {
        if (content == null || content.isBlank() || content.length() > 2000) {
            throw new IllegalArgumentException("消息内容需为 1 到 2000 字");
        }
        Conversation conversation = conversations.lockById(conversationId);
        if (conversation == null || (!userId.equals(conversation.getPublisherId())
                && !userId.equals(conversation.getApplicantId()))) {
            throw new SecurityException("会话不存在或无权访问");
        }
        Item item = items.selectById(conversation.getItemId());
        if (!"open".equals(conversation.getStatus()) || item == null || !"processing".equals(item.getStatus())) {
            throw new IllegalStateException("会话已关闭，不能继续发送");
        }
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversationId);
        message.setSenderId(userId);
        message.setContent(content.trim());
        message.setCreatedAt(LocalDateTime.now());
        messages.insert(message);
        if (userId.equals(conversation.getPublisherId())) {
            conversations.markPublisherRead(conversationId, message.getId());
        } else {
            conversations.markApplicantRead(conversationId, message.getId());
        }
        return message;
    }

    @Override
    @Transactional
    public void markRead(Long conversationId, Long userId) {
        Conversation conversation = requireParticipant(conversationId, userId);
        long latestId = messages.latestId(conversationId);
        if (userId.equals(conversation.getPublisherId())) {
            conversations.markPublisherRead(conversationId, latestId);
        } else {
            conversations.markApplicantRead(conversationId, latestId);
        }
    }

    @Override
    @Transactional
    public void closeByItemId(Long itemId) {
        conversations.closeByItemId(itemId);
    }

    @Override
    public long countUnread(Long userId) {
        return conversations.countUnread(userId);
    }

    private void addItemTitle(Conversation conversation) {
        Item item = items.selectById(conversation.getItemId());
        conversation.setItemTitle(item == null ? "物品已移除" : item.getTitle());
    }
}
