package com.spt.sptpicturebackend.search.persistence;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("search_index_state")
public class SearchIndexState {
    @TableId private Long id;
    @TableField("building_index") private String buildingIndex;
    @TableField("rebuild_task_id") private Long rebuildTaskId;
    @TableField("export_cursor") private Long exportCursor;
    @TableField("reconcile_lock_owner") private String reconcileLockOwner;
    @TableField("reconcile_lock_until") private Date reconcileLockUntil;
    @TableField("last_reconcile_time") private Date lastReconcileTime;
    @TableField("last_reconcile_diff_count") private Integer lastReconcileDiffCount;
    @TableField("update_time") private Date updateTime;
}
