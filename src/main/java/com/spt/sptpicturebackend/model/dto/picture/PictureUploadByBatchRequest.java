package com.spt.sptpicturebackend.model.dto.picture;

import lombok.Data;

/**
 * @author zhiyuan
 */
@Data
public class PictureUploadByBatchRequest {  
  
    /**  
     * 搜索词  
     */  
    private String searchText;  
  
    /**  
     * 抓取数量  
     */  
    private Integer count = 10;  
}
