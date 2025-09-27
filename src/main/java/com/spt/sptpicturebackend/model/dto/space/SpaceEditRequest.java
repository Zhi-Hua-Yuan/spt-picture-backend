package com.spt.sptpicturebackend.model.dto.space;

import lombok.Data;

import java.io.Serializable;

/**
 * @author zhiyuan
 */
@Data
public class SpaceEditRequest implements Serializable {

    /**
     * 空间 id
     */
    private Long id;

    /**
     * 空间名称
     */
    private String spaceName;

    private static final long serialVersionUID = 1L;
}
