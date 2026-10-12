#!/bin/sh
set -eu

mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" <<-EOSQL
CREATE DATABASE IF NOT EXISTS spt_picture_media CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS spt_picture_search CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'picture_core'@'%' IDENTIFIED BY '${MYSQL_CORE_PASSWORD:-change-me}';
CREATE USER IF NOT EXISTS 'picture_media'@'%' IDENTIFIED BY '${MYSQL_MEDIA_PASSWORD:-change-me}';
CREATE USER IF NOT EXISTS 'picture_search'@'%' IDENTIFIED BY '${MYSQL_SEARCH_PASSWORD:-change-me}';
GRANT ALL PRIVILEGES ON spt_picture.* TO 'picture_core'@'%';
GRANT ALL PRIVILEGES ON spt_picture_media.* TO 'picture_media'@'%';
GRANT ALL PRIVILEGES ON spt_picture_search.* TO 'picture_search'@'%';
FLUSH PRIVILEGES;
EOSQL
