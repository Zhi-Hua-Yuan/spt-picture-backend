package com.spt.sptpicturebackend.messaging;

public final class PictureEventType {
    private PictureEventType() {
    }

    public static final String PROCESS_REQUESTED = "PICTURE_PROCESS_REQUESTED";
    public static final String PICTURE_CHANGED = "PICTURE_CHANGED";
    public static final String BATCH_IMPORT_REQUESTED = "BATCH_IMPORT_REQUESTED";
    public static final String BATCH_IMPORT_ITEM_REQUESTED = "BATCH_IMPORT_ITEM_REQUESTED";
    public static final String PICTURE_DELETED = "PICTURE_DELETED";
    public static final String INDEX_REBUILD_REQUESTED = "INDEX_REBUILD_REQUESTED";
    public static final String INDEX_RECONCILE_REQUESTED = "INDEX_RECONCILE_REQUESTED";
    public static final String FILES_DELETE_REQUESTED = "PICTURE_FILES_DELETE_REQUESTED";
}
