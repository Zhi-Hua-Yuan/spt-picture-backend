package com.spt.sptpicturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("outbox_event")
public class OutboxEvent implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventId;
    private Long aggregateId;
    private String eventType;
    private String routingKey;
    private Long version;
    private String payload;
    private String status;
    private Integer retryCount;
    private Date nextRetryTime;
    private Date sentTime;
    private Date lockedAt;
    private String lockOwner;
    private String lastError;
    private Date createTime;
    private Date updateTime;
    private static final long serialVersionUID = 1L;
}
