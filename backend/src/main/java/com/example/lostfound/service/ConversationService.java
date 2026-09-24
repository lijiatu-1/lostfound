package com.example.lostfound.service;

import com.example.lostfound.entity.Application;
import com.example.lostfound.entity.ChatMessage;
import com.example.lostfound.entity.Conversation;
import java.util.List;

public interface ConversationService {
    Conversation createForAcceptedApplication(Application application, Long publisherId);
    List<Conversation> listForUser(Long userId);
    Conversation requireParticipant(Long conversationId, Long userId);
    List<ChatMessage> listMessages(Long conversationId, Long userId, int limit);
    ChatMessage send(Long conversationId, Long userId, String content);
    void markRead(Long conversationId, Long userId);
    void closeByItemId(Long itemId);
    long countUnread(Long userId);
}
