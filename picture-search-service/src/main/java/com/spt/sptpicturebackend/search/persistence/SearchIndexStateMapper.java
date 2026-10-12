package com.spt.sptpicturebackend.search.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SearchIndexStateMapper extends BaseMapper<SearchIndexState> {
    @Update("UPDATE search_index_state SET reconcile_lock_owner = #{owner}, reconcile_lock_until = DATE_ADD(NOW(), INTERVAL 60 MINUTE) WHERE id = 1 AND (reconcile_lock_until IS NULL OR reconcile_lock_until < NOW())")
    int tryAcquireReconcileLock(String owner);

    @Update("UPDATE search_index_state SET reconcile_lock_owner = NULL, reconcile_lock_until = NULL WHERE id = 1 AND reconcile_lock_owner = #{owner}")
    int releaseReconcileLock(String owner);
}
