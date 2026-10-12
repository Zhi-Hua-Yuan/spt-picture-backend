package com.spt.sptpicturebackend.media.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("media_job")
public class MediaJob {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("source_task_id") private Long sourceTaskId;
    @TableField("job_type") private String jobType;
    @TableField("picture_id") private Long pictureId;
    @TableField("expected_version") private Long expectedVersion;
    @TableField("request_event_id") private String requestEventId;
    @TableField("status") private String status;
    @TableField("attempt_count") private Integer attemptCount;
    @TableField("error_code") private String errorCode;
    @TableField("error_message") private String errorMessage;
    @TableField("request_payload") private String requestPayload;
    @TableField("create_time") private Date createTime;
    @TableField("update_time") private Date updateTime;
}
