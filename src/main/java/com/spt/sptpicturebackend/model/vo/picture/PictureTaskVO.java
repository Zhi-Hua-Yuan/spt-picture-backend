package com.spt.sptpicturebackend.model.vo.picture;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class PictureTaskVO implements Serializable {
    private Long id;
    private String taskType;
    private String status;
    private Integer progress;
    private Integer totalCount;
    private Integer successCount;
    private Integer failedCount;
    private Integer retryCount;
    private Long pictureId;
    private Long spaceId;
    private String errorMessage;
    private Date startTime;
    private Date endTime;
    private Date createTime;
    private java.util.List<PictureTaskItemVO> items;
}
