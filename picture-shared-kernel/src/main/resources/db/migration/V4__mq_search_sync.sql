CREATE TABLE IF NOT EXISTS search_index_state
(
    id bigint PRIMARY KEY,
    buildingIndex varchar(255) NULL,
    rebuildTaskId bigint NULL,
    reconcileLockOwner varchar(128) NULL,
    reconcileLockUntil datetime NULL,
    lastReconcileTime datetime NULL,
    lastReconcileDiffCount int NOT NULL DEFAULT 0,
    updateTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO search_index_state (id)
VALUES (1)
ON DUPLICATE KEY UPDATE id = id;

CREATE INDEX idx_outbox_claim ON outbox_event (status, nextRetryTime, id);
CREATE INDEX idx_outbox_sent_time ON outbox_event (status, sentTime);
CREATE INDEX idx_mq_consume_time ON mq_consume_log (consumedTime);
