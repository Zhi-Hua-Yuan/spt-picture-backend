package com.spt.sptpicturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("picture_task")
public class PictureTask implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String taskType;
    private Long userId;
    private Long spaceId;
    private Long pictureId;
    private String status;
    private Integer progress;
    private Integer totalCount;
    private Integer successCount;
    private Integer failedCount;
    private Integer retryCount;
    private String errorMessage;
    private String requestParams;
    private Date startTime;
    private Date endTime;
    private Date createTime;
    private Date updateTime;
    @TableLogic
    private Integer isDelete;
    private static final long serialVersionUID = 1L;
}
