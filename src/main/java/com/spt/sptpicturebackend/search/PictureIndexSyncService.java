package com.spt.sptpicturebackend.search;

import com.spt.sptpicturebackend.model.entity.Picture;
import com.spt.sptpicturebackend.service.PictureService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.annotation.Resource;

/** Best-effort direct synchronizer used only until the Outbox/MQ phase is enabled. */
@Slf4j
@Service
public class PictureIndexSyncService {
    @Resource
    private PictureSearchIndexService indexService;
    @Resource
    @Lazy
    private PictureService pictureService;

    public void syncByIdAfterCommit(Long pictureId) {
        if (pictureId == null) return;
        runAfterCommit(() -> syncById(pictureId));
    }

    public void syncDeletedAfterCommit(Picture deletedPicture) {
        if (deletedPicture == null || deletedPicture.getId() == null) return;
        runAfterCommit(() -> {
            try {
                indexService.deletePicture(deletedPicture);
            } catch (Exception e) {
                log.warn("picture delete index sync failed, pictureId={}", deletedPicture.getId(), e);
            }
        });
    }

    private void syncById(Long pictureId) {
        try {
            Picture picture = pictureService.getById(pictureId);
            if (picture != null) indexService.indexPicture(picture);
        } catch (Exception e) {
            log.warn("picture index sync failed, pictureId={}", pictureId, e);
        }
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
