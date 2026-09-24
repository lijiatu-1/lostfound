package com.example.lostfound.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("chat_messages")
public class ChatMessage {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("conversation_id")
    private Long conversationId;
    @TableField("sender_id")
    private Long senderId;
    private String content;
    @TableField("created_at")
    private LocalDateTime createdAt;
}
