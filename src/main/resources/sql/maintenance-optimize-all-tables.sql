-- 手动维护脚本：整理 ad_platform 数据库中的所有普通表，跳过视图。
-- 不属于数据库初始化步骤。请在停止压测或业务低峰时执行。
-- 请在同一个连接中按顺序执行整个脚本。
-- 整理需要临时磁盘空间，可能短暂阻塞读写；不会删除现有业务数据。

-- 保存原配置，避免表名拼接被默认长度限制截断。
SET @old_group_concat_max_len = @@SESSION.group_concat_max_len;
SET SESSION group_concat_max_len = 1048576;

-- 自动生成整理语句。
SELECT CONCAT(
    'OPTIMIZE TABLE ',
    GROUP_CONCAT(
        CONCAT('`ad_platform`.`', REPLACE(TABLE_NAME, '`', '``'), '`')
        ORDER BY TABLE_NAME
        SEPARATOR ', '
    )
) INTO @optimize_sql
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'ad_platform'
  AND TABLE_TYPE = 'BASE TABLE';

-- 空数据库直接返回提示。
SET @optimize_sql = COALESCE(
    @optimize_sql,
    'SELECT ''ad_platform 中没有可整理的表'' AS message'
);

-- 执行整理，检查每张表最终是否返回 status = OK。
-- InnoDB 出现 doing recreate + analyze instead 提示属于正常情况。
PREPARE optimize_stmt FROM @optimize_sql;
EXECUTE optimize_stmt;
DEALLOCATE PREPARE optimize_stmt;

-- 恢复当前连接的原配置。
SET SESSION group_concat_max_len = @old_group_concat_max_len;
