CREATE TABLE IF NOT EXISTS media_job
(
    id bigint PRIMARY KEY AUTO_INCREMENT,
    source_task_id bigint NOT NULL,
    job_type varchar(64) NOT NULL,
    picture_id bigint NULL,
    expected_version bigint NULL,
    request_event_id varchar(64) NOT NULL,
    status varchar(32) NOT NULL,
    attempt_count int NOT NULL DEFAULT 0,
    error_code varchar(128) NULL,
    error_message varchar(2000) NULL,
    request_payload json NULL,
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_media_job_request (request_event_id),
    UNIQUE KEY uk_media_job_source_task (source_task_id, job_type),
    INDEX idx_media_job_status (status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS media_job_item
(
    id bigint PRIMARY KEY AUTO_INCREMENT,
    media_job_id bigint NOT NULL,
    source_item_id bigint NULL,
    sequence_no int NOT NULL,
    source_url varchar(2048) NULL,
    original_object_key varchar(512) NULL,
    display_object_key varchar(512) NULL,
    thumbnail_object_key varchar(512) NULL,
    status varchar(32) NOT NULL DEFAULT 'PENDING',
    attempt_count int NOT NULL DEFAULT 0,
    error_message varchar(1000) NULL,
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_media_job_item_sequence (media_job_id, sequence_no),
    INDEX idx_media_job_item_source (source_item_id),
    CONSTRAINT fk_media_job_item_job FOREIGN KEY (media_job_id) REFERENCES media_job (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
    UNIQUE KEY uk_media_inbox_event (consumer_group, event_id),
    INDEX idx_media_inbox_claim (consumer_group, status, lease_until, id)
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
    UNIQUE KEY uk_media_consume_event (consumerGroup, eventId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
