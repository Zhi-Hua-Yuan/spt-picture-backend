-- Milestone 2: database-side uniqueness, leases and reliable dispatch metadata.
-- This migration is intentionally additive.  It must not be edited after release.

ALTER TABLE space
    ADD COLUMN activeOwnerId bigint
        GENERATED ALWAYS AS (CASE WHEN isDelete = 0 THEN userId ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_space_active_owner_type (activeOwnerId, spaceType);

ALTER TABLE space_user
    ADD COLUMN isDelete tinyint NOT NULL DEFAULT 0,
    ADD COLUMN activeMemberMarker tinyint
        GENERATED ALWAYS AS (CASE WHEN isDelete = 0 THEN 1 ELSE NULL END) STORED,
    DROP INDEX uk_spaceId_userId,
    ADD UNIQUE KEY uk_space_active_member (spaceId, userId, activeMemberMarker);

ALTER TABLE picture
    DROP INDEX idx_picture_taskItemId,
    ADD UNIQUE KEY uk_picture_taskItemId (taskItemId);

ALTER TABLE mq_consume_log
    ADD COLUMN status varchar(32) NOT NULL DEFAULT 'SUCCEEDED',
    ADD COLUMN leaseOwner varchar(128) NULL,
    ADD COLUMN leaseUntil datetime NULL,
    ADD COLUMN retryCount int NOT NULL DEFAULT 0,
    ADD COLUMN lastError varchar(2000) NULL,
    ADD COLUMN processedTime datetime NULL,
    ADD COLUMN updateTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

ALTER TABLE outbox_event
    ADD COLUMN exchangeName varchar(128) NOT NULL DEFAULT 'picture.events';

CREATE INDEX idx_mq_consume_claim ON mq_consume_log (consumerGroup, status, leaseUntil, id);
