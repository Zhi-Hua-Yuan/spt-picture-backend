package com.spt.sptpicturebackend.constant;

/**
 * @author zhiyuan
 */
public interface PictureConstant {

    /**
     * 图片审核通过
     */
    Integer REVIEW_PASS = 1;

    /**
     * 图片审核未通过
     */
    Integer REVIEW_REJECT = 2;

    /**
     * 管理员自动审核通过理由
     */
    String REVIEW_MESSAGE = "管理员自动审核通过";
}
