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
  
    private static final long serialVersionUID = 1L;  
}
