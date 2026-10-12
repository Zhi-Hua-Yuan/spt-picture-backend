CREATE TABLE IF NOT EXISTS search_index_state
(
    id bigint PRIMARY KEY,
    building_index varchar(255) NULL,
    rebuild_task_id bigint NULL,
    export_cursor bigint NULL,
    reconcile_lock_owner varchar(128) NULL,
    reconcile_lock_until datetime NULL,
    last_reconcile_time datetime NULL,
    last_reconcile_diff_count int NOT NULL DEFAULT 0,
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO search_index_state (id) VALUES (1)
ON DUPLICATE KEY UPDATE id = id;

CREATE TABLE IF NOT EXISTS inbox_event
(
    id bigint PRIMARY KEY AUTO_INCREMENT,
    consumer_group varchar(128) NOT NULL,
    event_id varchar(64) NOT NULL,
    status varchar(32) NOT NULL DEFAULT 'NEW',
    lease_owner varchar(128) NULL,
    lease_until datetime NULL,
    retry_count int NOT NULL DEFAULT 0,
    last_error varchar(2000) NULL,
    processed_time datetime NULL,
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_search_inbox_event (consumer_group, event_id),
    INDEX idx_search_inbox_claim (consumer_group, status, lease_until, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mq_consume_log
(
    id bigint PRIMARY KEY AUTO_INCREMENT,
    consumerGroup varchar(128) NOT NULL,
    eventId varchar(64) NOT NULL,
    consumedTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status varchar(32) NOT NULL DEFAULT 'NEW',
    leaseOwner varchar(128) NULL,
    leaseUntil datetime NULL,
    retryCount int NOT NULL DEFAULT 0,
    lastError varchar(2000) NULL,
    processedTime datetime NULL,
    updateTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_search_consume_event (consumerGroup, eventId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
