package com.spt.sptpicturebackend.messaging.v1;

/** Event names and routing keys introduced by the database-autonomy release. */
public final class PictureEventTypesV1 {
    private PictureEventTypesV1() {
    }

    public static final String PICTURE_PROCESSING_REQUESTED = "PictureProcessingRequestedV1";
    public static final String PICTURE_PROCESSING_SUCCEEDED = "PictureProcessingSucceededV1";
    public static final String PICTURE_PROCESSING_FAILED = "PictureProcessingFailedV1";
    public static final String BATCH_IMPORT_REQUESTED = "BatchImportRequestedV1";
    public static final String BATCH_ITEM_PREPARED = "BatchItemPreparedV1";
    public static final String BATCH_COMPLETED = "BatchCompletedV1";
    public static final String FILES_DELETE_REQUESTED = "FilesDeleteRequestedV1";
    public static final String PICTURE_SEARCH_UPSERTED = "PictureSearchUpsertedV1";
    public static final String PICTURE_SEARCH_DELETED = "PictureSearchDeletedV1";
    public static final String SEARCH_MAINTENANCE_REQUESTED = "SearchMaintenanceRequestedV1";
    public static final String SEARCH_MAINTENANCE_PROGRESS = "SearchMaintenanceProgressV1";
    public static final String SEARCH_MAINTENANCE_COMPLETED = "SearchMaintenanceCompletedV1";
    public static final String SEARCH_MAINTENANCE_FAILED = "SearchMaintenanceFailedV1";
}
