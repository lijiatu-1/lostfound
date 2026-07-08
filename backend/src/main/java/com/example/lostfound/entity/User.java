package com.example.lostfound.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("users")
public class User {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("openid")
    private String openid;

    @TableField("nickname")
    private String nickname;

    @TableField("avatar_url")
    private String avatarUrl;

    @TableField("status")
    private String status;

    @TableField("role")
    private String role;

    @TableField("real_name")
    private String realName;

    @TableField("student_id")
    private String studentId;

    @TableField("card_photo")
    private String cardPhoto;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}