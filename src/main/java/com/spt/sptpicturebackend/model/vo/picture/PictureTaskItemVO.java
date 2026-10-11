package com.spt.sptpicturebackend.model.vo.picture;

import lombok.Data;

import java.io.Serializable;

@Data
public class PictureTaskItemVO implements Serializable {
    private Long id;
    private Integer sequenceNo;
    private String sourceUrl;
    private Long pictureId;
    private String status;
    private String errorMessage;
    private Integer attemptCount;
}
