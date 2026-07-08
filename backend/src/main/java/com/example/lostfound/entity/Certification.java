package com.example.lostfound.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("certifications")
public class Certification {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("real_name")
    private String realName;

    @TableField("student_id")
    private String studentId;

    @TableField("card_photo")
    private String cardPhoto;

    @TableField("status")
    private String status;

    @TableField("reviewer_id")
    private Long reviewerId;

    @TableField("review_msg")
    private String reviewMsg;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
