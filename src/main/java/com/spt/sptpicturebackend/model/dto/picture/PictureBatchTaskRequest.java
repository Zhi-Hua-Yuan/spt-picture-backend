package com.spt.sptpicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

@Data
public class PictureBatchTaskRequest implements Serializable {
    private String searchText;
    private Integer count = 10;
    private String namePrefix;
    private Long spaceId;
}
