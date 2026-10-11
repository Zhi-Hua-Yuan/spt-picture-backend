package com.spt.sptpicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

@Data
public class PictureDlqReplayRequest implements Serializable {
    private String queue;
    private Integer maxMessages = 100;
}
