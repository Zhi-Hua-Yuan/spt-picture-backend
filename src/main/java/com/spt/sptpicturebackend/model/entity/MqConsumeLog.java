package com.spt.sptpicturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("mq_consume_log")
public class MqConsumeLog implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String consumerGroup;
    private String eventId;
    private Date consumedTime;
    private String status;
    private String leaseOwner;
    private Date leaseUntil;
    private Integer retryCount;
    private String lastError;
    private Date processedTime;
    private Date updateTime;
    private static final long serialVersionUID = 1L;
}
