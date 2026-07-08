package com.example.lostfound.service;

import com.example.lostfound.entity.Comment;

import java.util.List;

public interface CommentService {

    Comment findById(Long id);

    List<Comment> findByItemId(Long itemId);

    Comment save(Comment comment);

    void deleteById(Long commentId);

    int countByItemId(Long itemId);

    boolean hasRecentDuplicate(Long itemId, Long userId, String content);
}
