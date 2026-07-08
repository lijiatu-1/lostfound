package com.example.lostfound.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("applications")
public class Application {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("item_id")
    private Long itemId;

    @TableField("applicant_id")
    private Long applicantId;

    @TableField("type")
    private String type;

    @TableField("content")
    private String content;

    @TableField("images")
    private String images;

    @TableField("status")
    private String status;

    @TableField("created_at")
    private LocalDateTime createdAt;
}