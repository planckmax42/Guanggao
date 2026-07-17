USE ad_platform;

-- 本地开发专用 CDC 账号。生产环境应从密钥管理系统注入密码，并限制来源地址。
CREATE USER IF NOT EXISTS 'debezium'@'localhost' IDENTIFIED BY 'DbzLocal_2026!';
CREATE USER IF NOT EXISTS 'debezium'@'127.0.0.1' IDENTIFIED BY 'DbzLocal_2026!';

GRANT SELECT, RELOAD, SHOW DATABASES, REPLICATION SLAVE, REPLICATION CLIENT, LOCK TABLES
    ON *.* TO 'debezium'@'localhost';
GRANT SELECT, RELOAD, SHOW DATABASES, REPLICATION SLAVE, REPLICATION CLIENT, LOCK TABLES
    ON *.* TO 'debezium'@'127.0.0.1';

-- 兼容已经执行过 06 脚本的数据库，幂等补充按时间清理所需索引。
SET @created_at_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = 'ad_platform'
      AND table_name = 'outbox_message'
      AND index_name = 'idx_outbox_created_at'
);
SET @create_index_sql = IF(
    @created_at_index_exists = 0,
    'CREATE INDEX idx_outbox_created_at ON ad_platform.outbox_message (created_at)',
    'SELECT 1'
);
PREPARE create_outbox_index FROM @create_index_sql;
EXECUTE create_outbox_index;
DEALLOCATE PREPARE create_outbox_index;

INSERT INTO schema_version (version, description)
VALUES ('stage-7-debezium-cdc', 'Debezium MySQL binlog CDC for transactional outbox publishing')
ON DUPLICATE KEY UPDATE description = VALUES(description);
