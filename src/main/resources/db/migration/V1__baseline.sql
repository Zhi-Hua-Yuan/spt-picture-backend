CREATE TABLE IF NOT EXISTS user
(
    id bigint PRIMARY KEY AUTO_INCREMENT,
    userName varchar(256) NULL,
    userAccount varchar(256) NOT NULL,
    userPassword varchar(512) NOT NULL,
    userAvatar varchar(1024) NULL,
    userProfile varchar(512) NULL,
    userRole varchar(256) NOT NULL DEFAULT 'user',
    editTime timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    createTime timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updateTime timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    isDelete tinyint NOT NULL DEFAULT 0,
    UNIQUE KEY uk_userAccount (userAccount),
    INDEX idx_userName (userName)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS picture
(
    id bigint PRIMARY KEY AUTO_INCREMENT,
    url varchar(512) NOT NULL,
    name varchar(128) NOT NULL,
    introduction varchar(512) NULL,
    category varchar(64) NULL,
    tags varchar(512) NULL,
    picSize bigint NULL,
    picWidth int NULL,
    picHeight int NULL,
    picScale double NULL,
    picFormat varchar(32) NULL,
    userId bigint NOT NULL,
    createTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    editTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updateTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    isDelete tinyint NOT NULL DEFAULT 0,
    reviewStatus int NOT NULL DEFAULT 0,
    reviewMessage varchar(512) NULL,
    reviewerId bigint NULL,
    reviewTime datetime NULL,
    thumbnailUrl varchar(512) NULL,
    spaceId bigint NULL,
    picColor varchar(16) NULL,
    INDEX idx_name (name), INDEX idx_introduction (introduction), INDEX idx_category (category),
    INDEX idx_tags (tags), INDEX idx_userId (userId), INDEX idx_reviewStatus (reviewStatus),
    INDEX idx_spaceId (spaceId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS space
(
    id bigint PRIMARY KEY AUTO_INCREMENT,
    spaceName varchar(128) NULL,
    spaceLevel int DEFAULT 0,
    maxSize bigint DEFAULT 0,
    maxCount bigint DEFAULT 0,
    totalSize bigint DEFAULT 0,
    totalCount bigint DEFAULT 0,
    userId bigint NOT NULL,
    createTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    editTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updateTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    isDelete tinyint NOT NULL DEFAULT 0,
    spaceType int NOT NULL DEFAULT 0,
    INDEX idx_userId (userId), INDEX idx_spaceName (spaceName), INDEX idx_spaceLevel (spaceLevel),
    INDEX idx_spaceType (spaceType)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS space_user
(
    id bigint PRIMARY KEY AUTO_INCREMENT,
    spaceId bigint NOT NULL,
    userId bigint NOT NULL,
    spaceRole varchar(128) DEFAULT 'viewer',
    createTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updateTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_spaceId_userId (spaceId, userId),
    INDEX idx_spaceId (spaceId), INDEX idx_userId (userId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
