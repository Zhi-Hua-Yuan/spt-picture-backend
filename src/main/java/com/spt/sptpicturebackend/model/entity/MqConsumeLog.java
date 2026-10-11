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
    private static final long serialVersionUID = 1L;
}
