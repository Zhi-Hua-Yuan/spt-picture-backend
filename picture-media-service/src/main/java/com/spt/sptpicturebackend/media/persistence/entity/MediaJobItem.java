package com.spt.sptpicturebackend.media.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("media_job_item")
public class MediaJobItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("media_job_id") private Long mediaJobId;
    @TableField("source_item_id") private Long sourceItemId;
    @TableField("sequence_no") private Integer sequenceNo;
    @TableField("source_url") private String sourceUrl;
    @TableField("original_object_key") private String originalObjectKey;
    @TableField("display_object_key") private String displayObjectKey;
    @TableField("thumbnail_object_key") private String thumbnailObjectKey;
    @TableField("status") private String status;
    @TableField("attempt_count") private Integer attemptCount;
    @TableField("error_message") private String errorMessage;
    @TableField("create_time") private Date createTime;
    @TableField("update_time") private Date updateTime;
}
