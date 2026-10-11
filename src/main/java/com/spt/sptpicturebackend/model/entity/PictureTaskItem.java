package com.spt.sptpicturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("picture_task_item")
public class PictureTaskItem implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Integer sequenceNo;
    private String sourceUrl;
    private Long pictureId;
    private String status;
    private String errorMessage;
    private Integer attemptCount;
    private Date createTime;
    private Date updateTime;
    private static final long serialVersionUID = 1L;
}
