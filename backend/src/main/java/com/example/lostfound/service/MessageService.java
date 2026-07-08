package com.example.lostfound.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.lostfound.entity.Message;

import java.util.List;

public interface MessageService {

    Message findById(Long id);

    Page<Message> findByReceiverId(Long receiverId, int page, int pageSize);

    Message save(Message message);

    void markAsRead(Long id);

    void markAllAsRead(Long receiverId);

    Integer countUnread(Long receiverId);

    void sendClaimApplyMessage(Long receiverId, Long itemId, String applicantName);

    void sendHelpOfferMessage(Long receiverId, Long itemId, String helperName);

    void sendSystemNotice(Long receiverId, String title, String content);
}