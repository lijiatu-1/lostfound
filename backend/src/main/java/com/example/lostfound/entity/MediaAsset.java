package com.example.lostfound.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * A server-managed file.  Clients exchange its ID rather than a storage URL so
 * that ownership and visibility can be checked at every boundary.
 */
@Data
@TableName("media_assets")
public class MediaAsset {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("owner_id")
    private Long ownerId;

    /** item or certification */
    @TableField("purpose")
    private String purpose;

    @TableField("storage_key")
    private String storageKey;

    @TableField("original_filename")
    private String originalFilename;

    @TableField("content_type")
    private String contentType;

    @TableField("size_bytes")
    private Long sizeBytes;

    @TableField("width")
    private Integer width;

    @TableField("height")
    private Integer height;

    @TableField("is_public")
    private Boolean isPublic;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
