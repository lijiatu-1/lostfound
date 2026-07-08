package com.example.lostfound.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("comments")
public class Comment {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("item_id")
    private Long itemId;

    @TableField("user_id")
    private Long userId;

    @TableField("content")
    private String content;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
