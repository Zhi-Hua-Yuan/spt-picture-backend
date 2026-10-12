CREATE TABLE IF NOT EXISTS media_outbox_event
(
    id bigint PRIMARY KEY AUTO_INCREMENT,
    event_id varchar(64) NOT NULL,
    aggregate_id bigint NULL,
    event_type varchar(128) NOT NULL,
    routing_key varchar(256) NOT NULL,
    exchange_name varchar(256) NOT NULL,
    version bigint NOT NULL DEFAULT 1,
    payload json NOT NULL,
    status varchar(32) NOT NULL DEFAULT 'PENDING',
    retry_count int NOT NULL DEFAULT 0,
    next_retry_time datetime NULL,
    sent_time datetime NULL,
    locked_at datetime NULL,
    lock_owner varchar(128) NULL,
    last_error varchar(2000) NULL,
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_media_outbox_event (event_id),
    INDEX idx_media_outbox_pending (status, next_retry_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
