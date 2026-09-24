package com.example.lostfound.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("conversations")
public class Conversation {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("item_id")
    private Long itemId;
    @TableField("application_id")
    private Long applicationId;
    @TableField("publisher_id")
    private Long publisherId;
    @TableField("applicant_id")
    private Long applicantId;
    private String status;
    @TableField("publisher_read_id")
    private Long publisherReadId;
    @TableField("applicant_read_id")
    private Long applicantReadId;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("closed_at")
    private LocalDateTime closedAt;
    @TableField(exist = false)
    private String itemTitle;
}
