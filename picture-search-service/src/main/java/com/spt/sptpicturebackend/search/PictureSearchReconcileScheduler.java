package com.spt.sptpicturebackend.search;

import com.spt.sptpicturebackend.search.persistence.SearchIndexStateMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.UUID;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "picture.search", name = "reconcile-enabled", havingValue = "true", matchIfMissing = true)
public class PictureSearchReconcileScheduler {
    private final String owner = UUID.randomUUID().toString();
    @Resource private SearchIndexStateMapper stateMapper;
    @Resource private SearchTaskExecutor taskExecutor;

    @Scheduled(cron = "${picture.search.reconcile-cron:0 0 3 * * *}")
    public void schedule() {
        if (stateMapper.tryAcquireReconcileLock(owner) == 0) return;
        try {
            taskExecutor.runScheduledReconcile();
        } catch (Exception e) {
            log.warn("创建定时搜索对账任务失败", e);
            stateMapper.releaseReconcileLock(owner);
        }
    }
}
