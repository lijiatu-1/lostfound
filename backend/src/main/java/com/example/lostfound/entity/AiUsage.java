package com.example.lostfound.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

/** Daily per-user AI recognition quota counter. */
@Data
@TableName("ai_usage")
public class AiUsage {

    @TableField("user_id")
    private Long userId;

    @TableField("usage_date")
    private LocalDate usageDate;

    @TableField("request_count")
    private Integer requestCount;
}
