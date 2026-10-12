package com.spt.sptpicturebackend.messaging.v1;

import java.time.Instant;
import java.util.List;

/** JSON payload records shared by the three deployment units. */
public final class PicturePayloadsV1 {
    private PicturePayloadsV1() {
    }

    public record PictureProcessingRequested(
            Long pictureId,
            Long taskId,
            Long expectedVersion,
            String originalObjectKey
    ) {
    }

    public record PictureProcessingSucceeded(
            Long pictureId,
            Long taskId,
            Long expectedVersion,
            String reviewStatus,
            String reviewMessage,
            String displayObjectKey,
            String thumbnailObjectKey,
            String displayUrl,
            String thumbnailUrl,
            Long processedSize,
            Instant processedAt
    ) {
    }

    public record PictureProcessingFailed(
            Long pictureId,
            Long taskId,
            Long expectedVersion,
            String errorCode,
            String errorMessage,
            List<String> generatedObjectKeys
    ) {
    }

    public record BatchImportRequested(
            Long taskId,
            Long userId,
            Long spaceId,
            String searchText,
            Integer count,
            String namePrefix,
            List<RetryItem> retryItems
    ) {
    }

    public record RetryItem(Long itemId, Integer sequenceNo, String sourceUrl) {
    }

    public record BatchItemPrepared(
            Long taskId,
            Long itemId,
            Integer sequenceNo,
            Long userId,
            Long spaceId,
            String sourceUrl,
            String name,
            String originalObjectKey,
            String displayObjectKey,
            String thumbnailObjectKey,
            String url,
            String thumbnailUrl,
            String picColor,
            Long picSize,
            Integer picWidth,
            Integer picHeight,
            Double picScale,
            String picFormat,
            boolean success,
            String errorMessage
    ) {
    }

    public record BatchCompleted(Long taskId, int totalCount, int successCount, int failedCount) {
    }

    public record FilesDeleteRequested(List<String> objectKeys) {
    }

    public record PictureSearchSnapshot(
            Long pictureId,
            Long version,
            String url,
            String name,
            String introduction,
            String category,
            List<String> tags,
            Long picSize,
            Integer picWidth,
            Integer picHeight,
            Double picScale,
            String picFormat,
            Long userId,
            Long spaceId,
            String picColor,
            Integer reviewStatus,
            Integer processStatus,
            Integer isDelete,
            Instant createTime,
            Instant editTime,
            Instant updateTime
    ) {
    }

    public record PictureSearchDeleted(Long pictureId, Long version) {
    }

    public record SearchMaintenanceRequested(Long taskId, String mode) {
    }

    public record SearchMaintenanceProgress(Long taskId, int progress, int successCount, int totalCount) {
    }

    public record SearchMaintenanceCompleted(Long taskId, int successCount, int totalCount) {
    }

    public record SearchMaintenanceFailed(Long taskId, String errorMessage) {
    }
}
