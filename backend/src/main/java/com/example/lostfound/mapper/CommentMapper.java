package com.example.lostfound.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.lostfound.entity.Comment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CommentMapper extends BaseMapper<Comment> {

    @Select("SELECT * FROM comments WHERE item_id = #{itemId} ORDER BY created_at ASC")
    List<Comment> findByItemId(@Param("itemId") Long itemId);

    @Select("SELECT COUNT(*) FROM comments WHERE item_id = #{itemId}")
    int countByItemId(@Param("itemId") Long itemId);

    @Select("SELECT COUNT(*) FROM comments WHERE item_id = #{itemId} AND user_id = #{userId} AND content = #{content} AND created_at > DATE_SUB(NOW(), INTERVAL 5 MINUTE)")
    int countRecentDuplicate(@Param("itemId") Long itemId, @Param("userId") Long userId, @Param("content") String content);
}
