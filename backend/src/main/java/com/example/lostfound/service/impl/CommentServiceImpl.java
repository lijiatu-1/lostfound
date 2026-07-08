package com.example.lostfound.service.impl;

import com.example.lostfound.entity.Comment;
import com.example.lostfound.mapper.CommentMapper;
import com.example.lostfound.service.CommentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CommentServiceImpl implements CommentService {

    private final CommentMapper commentMapper;

    public CommentServiceImpl(CommentMapper commentMapper) {
        this.commentMapper = commentMapper;
    }

    @Override
    public Comment findById(Long id) {
        return commentMapper.selectById(id);
    }

    @Override
    public List<Comment> findByItemId(Long itemId) {
        return commentMapper.findByItemId(itemId);
    }

    @Override
    @Transactional
    public Comment save(Comment comment) {
        comment.setCreatedAt(LocalDateTime.now());
        commentMapper.insert(comment);
        return comment;
    }

    @Override
    @Transactional
    public void deleteById(Long commentId) {
        commentMapper.deleteById(commentId);
    }

    @Override
    public int countByItemId(Long itemId) {
        return commentMapper.countByItemId(itemId);
    }

    @Override
    public boolean hasRecentDuplicate(Long itemId, Long userId, String content) {
        return commentMapper.countRecentDuplicate(itemId, userId, content) > 0;
    }
}
