package com.example.lostfound.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.lostfound.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {
    @Select("SELECT * FROM conversations WHERE id = #{id} FOR UPDATE")
    Conversation lockById(@Param("id") Long id);

    @Update("UPDATE conversations SET status = 'closed', closed_at = NOW() WHERE item_id = #{itemId} AND status = 'open'")
    int closeByItemId(@Param("itemId") Long itemId);

    @Update("UPDATE conversations SET publisher_read_id = GREATEST(publisher_read_id, #{messageId}) WHERE id = #{id}")
    int markPublisherRead(@Param("id") Long id, @Param("messageId") Long messageId);

    @Update("UPDATE conversations SET applicant_read_id = GREATEST(applicant_read_id, #{messageId}) WHERE id = #{id}")
    int markApplicantRead(@Param("id") Long id, @Param("messageId") Long messageId);

    @Select("SELECT COALESCE(SUM(CASE WHEN c.publisher_id = #{userId} AND m.sender_id <> #{userId} AND m.id > c.publisher_read_id THEN 1 WHEN c.applicant_id = #{userId} AND m.sender_id <> #{userId} AND m.id > c.applicant_read_id THEN 1 ELSE 0 END), 0) FROM conversations c LEFT JOIN chat_messages m ON m.conversation_id = c.id WHERE c.publisher_id = #{userId} OR c.applicant_id = #{userId}")
    long countUnread(@Param("userId") Long userId);
}
