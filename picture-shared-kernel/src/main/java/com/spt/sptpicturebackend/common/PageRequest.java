package com.spt.sptpicturebackend.common;

import lombok.Data;

/**
 * @author zhiyuan
 */
@Data
public class PageRequest {

    /**
     * 当前页码
     */
    private int current;

    /**
     * 每页显示条数，默认为 10
     */
    private int pageSize = 10;

    /**
     * 排序字段
     */
    private String sortField;

    /**
     * 排序顺序（默认排序）
     */
    private String sortOrder = "descend";
}
