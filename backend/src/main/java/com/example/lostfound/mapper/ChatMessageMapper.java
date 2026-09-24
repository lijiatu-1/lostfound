package com.example.lostfound.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.lostfound.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
    @Select("SELECT * FROM chat_messages WHERE conversation_id = #{conversationId} ORDER BY id DESC LIMIT #{limit}")
    List<ChatMessage> recent(@Param("conversationId") Long conversationId, @Param("limit") int limit);

    @Select("SELECT COALESCE(MAX(id), 0) FROM chat_messages WHERE conversation_id = #{conversationId}")
    long latestId(@Param("conversationId") Long conversationId);
}
