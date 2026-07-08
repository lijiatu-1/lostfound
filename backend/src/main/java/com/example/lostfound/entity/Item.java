package com.example.lostfound.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("items")
public class Item {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("publisher_id")
    private Long publisherId;

    @TableField("type")
    private String type;

    @TableField("category")
    private String category;

    @TableField("title")
    private String title;

    @TableField("description")
    private String description;

    @TableField("phone")
    private String phone;

    @TableField("location_name")
    private String locationName;

    @TableField("location_lat")
    private Double locationLat;

    @TableField("location_lng")
    private Double locationLng;

    @TableField("images")
    private String images;

    @TableField("tags")
    private String tags;

    @TableField("status")
    private String status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("expire_at")
    private LocalDateTime expireAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}