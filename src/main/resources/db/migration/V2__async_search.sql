ALTER TABLE picture
    ADD COLUMN processStatus int NOT NULL DEFAULT 2 COMMENT '处理状态：0-PENDING 1-PROCESSING 2-SUCCEEDED 3-FAILED',
    ADD COLUMN processMessage varchar(512) NULL,
    ADD COLUMN version bigint NOT NULL DEFAULT 1,
    ADD COLUMN originalObjectKey varchar(512) NULL,
    ADD COLUMN displayObjectKey varchar(512) NULL,
    ADD COLUMN thumbnailObjectKey varchar(512) NULL;

CREATE INDEX idx_picture_processStatus ON picture (processStatus);
CREATE INDEX idx_picture_version ON picture (version);
CREATE INDEX idx_picture_scope_status ON picture (spaceId, reviewStatus, processStatus, isDelete);

UPDATE picture SET processStatus = 2 WHERE processStatus IS NULL;
UPDATE picture SET version = 1 WHERE version IS NULL OR version < 1;

CREATE TABLE IF NOT EXISTS picture_task
(
    id bigint PRIMARY KEY,
    taskType varchar(64) NOT NULL,
    userId bigint NOT NULL,
    spaceId bigint NULL,
    pictureId bigint NULL,
    status varchar(32) NOT NULL,
    progress int NOT NULL DEFAULT 0,
    successCount int NOT NULL DEFAULT 0,
    failedCount int NOT NULL DEFAULT 0,
    errorMessage varchar(2000) NULL,
    requestParams json NULL,
    startTime datetime NULL,
    endTime datetime NULL,
    createTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updateTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    isDelete tinyint NOT NULL DEFAULT 0,
    INDEX idx_task_user (userId), INDEX idx_task_status (taskType, status), INDEX idx_task_create (createTime)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS picture_task_item
(
    id bigint PRIMARY KEY AUTO_INCREMENT,
    taskId bigint NOT NULL,
    sequenceNo int NOT NULL,
    sourceUrl varchar(2048) NULL,
    pictureId bigint NULL,
    status varchar(32) NOT NULL DEFAULT 'PENDING',
    errorMessage varchar(1000) NULL,
    createTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updateTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_task_sequence (taskId, sequenceNo), INDEX idx_task_item_task (taskId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS outbox_event
(
    id bigint PRIMARY KEY AUTO_INCREMENT,
    eventId varchar(64) NOT NULL,
    aggregateId bigint NULL,
    eventType varchar(64) NOT NULL,
    routingKey varchar(128) NOT NULL,
    version bigint NOT NULL DEFAULT 1,
    payload json NOT NULL,
    status varchar(32) NOT NULL DEFAULT 'PENDING',
    retryCount int NOT NULL DEFAULT 0,
    nextRetryTime datetime NULL,
    sentTime datetime NULL,
    createTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updateTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_outbox_event (eventId), INDEX idx_outbox_status (status, nextRetryTime)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mq_consume_log
(
    id bigint PRIMARY KEY AUTO_INCREMENT,
    consumerGroup varchar(128) NOT NULL,
    eventId varchar(64) NOT NULL,
    consumedTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_consume_event (consumerGroup, eventId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
