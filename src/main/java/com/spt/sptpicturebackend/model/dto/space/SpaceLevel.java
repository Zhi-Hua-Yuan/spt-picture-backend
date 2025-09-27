package com.spt.sptpicturebackend.model.dto.space;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author zhiyuan
 */
@Data
@AllArgsConstructor
public class SpaceLevel {

    private int value;

    private String text;

    private long maxCount;

    private long maxSize;
}
