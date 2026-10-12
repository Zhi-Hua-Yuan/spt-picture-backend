package com.spt.sptpicturebackend.media.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("inbox_event")
public class MediaInboxEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("consumer_group") private String consumerGroup;
    @TableField("event_id") private String eventId;
    private String status;
    @TableField("lease_owner") private String leaseOwner;
    @TableField("lease_until") private Date leaseUntil;
    @TableField("retry_count") private Integer retryCount;
    @TableField("last_error") private String lastError;
    @TableField("processed_time") private Date processedTime;
    @TableField("create_time") private Date createTime;
    @TableField("update_time") private Date updateTime;
}
