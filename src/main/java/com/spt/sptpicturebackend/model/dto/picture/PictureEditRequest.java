package com.spt.sptpicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * @author zhiyuan
 */
@Data
public class PictureEditRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 图片名称
     */
    private String name;

    /**
     * 简介
     */
    private String introduction;

    /**
     * 图片分类
     */
    private String category;

    /**
     * 图片标签 JSON数组
     */
    private String tags;

    private static final long serialVersionUID = 1L;

}
