package com.example.lostfound.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.lostfound.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    // findByReceiverId 已改用 MyBatis-Plus selectPage（见 MessageServiceImpl），删除死代码

    @Select("SELECT COUNT(*) FROM messages WHERE receiver_id = #{receiverId} AND is_read = false")
    Integer countUnread(@Param("receiverId") Long receiverId);

    @Update("UPDATE messages SET is_read = true WHERE id = #{id}")
    int markAsRead(@Param("id") Long id);

    @Update("UPDATE messages SET is_read = true WHERE receiver_id = #{receiverId}")
    int markAllAsRead(@Param("receiverId") Long receiverId);
}