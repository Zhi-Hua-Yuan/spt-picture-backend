package com.spt.sptpicturebackend.messaging.v1;

/** Versioned queue names. Legacy queues remain untouched during migration. */
public final class PictureRoutingV1 {
    private PictureRoutingV1() {
    }

    public static final String EVENTS_EXCHANGE = "picture.events.v1";
    public static final String RETRY_EXCHANGE = "picture.retry.v1";
    public static final String DLX_EXCHANGE = "picture.dlx.v1";
    public static final String PARKING_EXCHANGE = "picture.parking.v1";
    public static final String PARKING_QUEUE = "picture.parking.v1.q";

    public static final String MEDIA_PROCESS_QUEUE = "picture.media.process.v1.q";
    public static final String MEDIA_BATCH_QUEUE = "picture.media.batch.v1.q";
    public static final String MEDIA_FILE_DELETE_QUEUE = "picture.media.file-delete.v1.q";
    public static final String CORE_MEDIA_RESULT_QUEUE = "picture.core.media-result.v1.q";
    public static final String SEARCH_INDEX_QUEUE = "picture.search.index.v1.q";
    public static final String SEARCH_MAINTENANCE_QUEUE = "picture.search.maintenance.v1.q";
    public static final String CORE_SEARCH_RESULT_QUEUE = "picture.core.search-result.v1.q";

    public static final String MEDIA_PROCESS_REQUESTED = "picture.media.process.requested.v1";
    public static final String MEDIA_BATCH_REQUESTED = "picture.media.batch.requested.v1";
    public static final String MEDIA_FILE_DELETE_REQUESTED = "picture.media.file-delete.requested.v1";
    public static final String MEDIA_RESULT = "picture.media.result.v1";
    public static final String SEARCH_INDEX_UPSERTED = "picture.search.upserted.v1";
    public static final String SEARCH_INDEX_DELETED = "picture.search.deleted.v1";
    public static final String SEARCH_MAINTENANCE_REQUESTED = "picture.search.maintenance.requested.v1";
    public static final String SEARCH_MAINTENANCE_RESULT = "picture.search.maintenance.result.v1";

    public static final String MEDIA_PROCESS_RETRY = "picture.media.process.retry.v1";
    public static final String MEDIA_BATCH_RETRY = "picture.media.batch.retry.v1";
    public static final String MEDIA_FILE_DELETE_RETRY = "picture.media.file-delete.retry.v1";
    public static final String SEARCH_INDEX_RETRY = "picture.search.index.retry.v1";
    public static final String SEARCH_MAINTENANCE_RETRY = "picture.search.maintenance.retry.v1";

    public static final String MEDIA_PROCESS_DLQ = "picture.media.process.dlq.v1";
    public static final String MEDIA_BATCH_DLQ = "picture.media.batch.dlq.v1";
    public static final String MEDIA_FILE_DELETE_DLQ = "picture.media.file-delete.dlq.v1";
    public static final String SEARCH_INDEX_DLQ = "picture.search.index.dlq.v1";
    public static final String SEARCH_MAINTENANCE_DLQ = "picture.search.maintenance.dlq.v1";
    public static final String CORE_MEDIA_RESULT_RETRY = "picture.core.media-result.retry.v1";
    public static final String CORE_MEDIA_RESULT_DLQ = "picture.core.media-result.dlq.v1";
}
