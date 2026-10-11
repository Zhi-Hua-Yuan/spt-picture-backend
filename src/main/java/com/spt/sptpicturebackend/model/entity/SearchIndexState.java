package com.spt.sptpicturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("search_index_state")
public class SearchIndexState implements Serializable {
    @TableId
    private Long id;
    private String buildingIndex;
    private Long rebuildTaskId;
    private String reconcileLockOwner;
    private Date reconcileLockUntil;
    private Date lastReconcileTime;
    private Integer lastReconcileDiffCount;
    private Date updateTime;
}
