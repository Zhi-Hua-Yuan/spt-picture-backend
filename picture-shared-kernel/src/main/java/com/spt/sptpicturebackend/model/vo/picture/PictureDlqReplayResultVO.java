package com.spt.sptpicturebackend.model.vo.picture;

import lombok.Data;

import java.io.Serializable;

@Data
public class PictureDlqReplayResultVO implements Serializable {
    private String queue;
    private Integer requested;
    private Integer replayed;
    private Integer failed;
    private Integer remaining;
}
