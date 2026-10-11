package com.spt.sptpicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * @author zhiyuan
 */
@Data
public class PictureUploadRequest implements Serializable {

    /**
     * 图片 id（用于修改）
     */
    private Long id;

    /**
     * 文件地址
     */
    private String fileUrl;

    /**
     * 图片名称
     */
    private String picName;

    /**
     * 空间 id
     */
    private Long spaceId;

    /** Internal batch item id used for idempotent asynchronous imports. */
    private Long taskItemId;

    /** Internal async idempotency coordinates; never accepted from public API callers. */
    private Long taskId;
    private String eventId;

    private static final long serialVersionUID = 1L;
}

