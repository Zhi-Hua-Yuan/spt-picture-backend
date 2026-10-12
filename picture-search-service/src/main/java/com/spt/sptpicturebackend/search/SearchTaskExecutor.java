package com.spt.sptpicturebackend.search;

import com.spt.sptpicturebackend.messaging.v1.EventEnvelopeV1;
import com.spt.sptpicturebackend.messaging.v1.PicturePayloadsV1;
import com.spt.sptpicturebackend.search.v1.SearchContractsV1;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

/** Search-owned rebuild/reconcile execution. The source of truth is Core's snapshot HTTP API. */
@Slf4j
@Service
public class SearchTaskExecutor {
    @Resource private PictureSearchIndexService indexService;
    @Resource private CoreSnapshotClient coreSnapshotClient;

    public void handleMaintenance(EventEnvelopeV1<PicturePayloadsV1.SearchMaintenanceRequested> envelope) {
        execute(envelope.payload().mode());
    }

    public void runScheduledReconcile() {
        execute("RECONCILE");
    }

    private void execute(String mode) {
        String index = null;
        try {
            if ("REBUILD".equalsIgnoreCase(mode)) {
                index = indexService.createRebuildIndex();
                long afterId = 0L;
                while (true) {
                    SearchContractsV1.SnapshotPage page = coreSnapshotClient.page(afterId, 500);
                    if (page.items().isEmpty()) break;
                    indexService.bulkIndexSnapshots(index, page.items());
                    if (!page.hasMore() || page.nextAfterId() == null || page.nextAfterId() <= afterId) break;
                    afterId = page.nextAfterId();
                }
                indexService.finishRebuild(index);
            } else {
                long afterId = 0L;
                while (true) {
                    SearchContractsV1.SnapshotPage page = coreSnapshotClient.page(afterId, 500);
                    if (page.items().isEmpty()) break;
                    for (SearchContractsV1.Snapshot snapshot : page.items()) indexService.indexSnapshot(snapshot);
                    if (!page.hasMore() || page.nextAfterId() == null || page.nextAfterId() <= afterId) break;
                    afterId = page.nextAfterId();
                }
            }
        } catch (Exception error) {
            if (index != null) indexService.cancelRebuild(index);
            log.error("search maintenance failed, mode={}", mode, error);
            throw new IllegalStateException("搜索维护失败", error);
        }
    }
}
