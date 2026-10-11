ALTER TABLE picture
    ADD COLUMN processTaskId bigint NULL,
    ADD COLUMN taskItemId bigint NULL,
    ADD INDEX idx_picture_processTaskId (processTaskId),
    ADD INDEX idx_picture_taskItemId (taskItemId);

ALTER TABLE picture_task
    ADD COLUMN totalCount int NOT NULL DEFAULT 1,
    ADD COLUMN retryCount int NOT NULL DEFAULT 0;

ALTER TABLE picture_task_item
    ADD COLUMN attemptCount int NOT NULL DEFAULT 0;

ALTER TABLE outbox_event
    ADD COLUMN lockedAt datetime NULL,
    ADD COLUMN lockOwner varchar(128) NULL,
    ADD COLUMN lastError varchar(2000) NULL;
