package com.spt.sptpicturebackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spt.sptpicturebackend.model.entity.SearchIndexState;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SearchIndexStateMapper extends BaseMapper<SearchIndexState> {
    @Update("UPDATE search_index_state SET reconcileLockOwner = #{owner}, reconcileLockUntil = DATE_ADD(NOW(), INTERVAL 60 MINUTE) WHERE id = 1 AND (reconcileLockUntil IS NULL OR reconcileLockUntil < NOW())")
    int tryAcquireReconcileLock(String owner);

    @Update("UPDATE search_index_state SET reconcileLockOwner = NULL, reconcileLockUntil = NULL WHERE id = 1 AND reconcileLockOwner = #{owner}")
    int releaseReconcileLock(String owner);
}
