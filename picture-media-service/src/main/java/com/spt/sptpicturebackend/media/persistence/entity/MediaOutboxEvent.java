package com.spt.sptpicturebackend.media.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("media_outbox_event")
public class MediaOutboxEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("event_id") private String eventId;
    @TableField("aggregate_id") private Long aggregateId;
    @TableField("event_type") private String eventType;
    @TableField("routing_key") private String routingKey;
    @TableField("exchange_name") private String exchangeName;
    private Long version;
    private String payload;
    private String status;
    @TableField("retry_count") private Integer retryCount;
    @TableField("next_retry_time") private Date nextRetryTime;
    @TableField("sent_time") private Date sentTime;
    @TableField("locked_at") private Date lockedAt;
    @TableField("lock_owner") private String lockOwner;
    @TableField("last_error") private String lastError;
    @TableField("create_time") private Date createTime;
    @TableField("update_time") private Date updateTime;
}
