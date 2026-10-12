package com.spt.sptpicturebackend.model.vo.picture;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class PictureSearchPageVO implements Serializable {
    private List<PictureVO> records = new ArrayList<>();
    private long total;
    private long current;
    private long pageSize;
    private boolean degraded;
}
