-- US-01 审计字段与 US-02 历史版本兼容。可重复执行，不改写历史操作者。
-- 已有数据库先备份，再在目标数据库中执行；本轮自动验证仅针对隔离测试库。
SET @migration_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 't_course' AND column_name = 'created_by') = 0,
    'ALTER TABLE t_course ADD COLUMN created_by VARCHAR(128) NULL', 'SELECT 1');
PREPARE course_migration FROM @migration_sql;
EXECUTE course_migration;
DEALLOCATE PREPARE course_migration;

SET @migration_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 't_course' AND column_name = 'updated_by') = 0,
    'ALTER TABLE t_course ADD COLUMN updated_by VARCHAR(128) NULL', 'SELECT 1');
PREPARE course_migration FROM @migration_sql;
EXECUTE course_migration;
DEALLOCATE PREPARE course_migration;

SET @migration_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 't_course_content_revision' AND column_name = 'publish_version') = 0,
    'ALTER TABLE t_course_content_revision ADD COLUMN publish_version INT NULL', 'SELECT 1');
PREPARE course_migration FROM @migration_sql;
EXECUTE course_migration;
DEALLOCATE PREPARE course_migration;

SET @migration_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 't_course_content_revision' AND column_name = 'lock_version') = 0,
    'ALTER TABLE t_course_content_revision ADD COLUMN lock_version INT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE course_migration FROM @migration_sql;
EXECUTE course_migration;
DEALLOCATE PREPARE course_migration;

UPDATE t_course_content_revision SET publish_version = version
WHERE status = 'PUBLISHED' AND publish_version IS NULL;
UPDATE t_course_content_revision SET lock_version = 0 WHERE lock_version IS NULL;

-- 旧重复草稿保留为 SUPERSEDED；仅保留 ID 最新的一份可编辑草稿。
UPDATE t_course_content_revision older
JOIN t_course_content_revision newer ON older.course_id = newer.course_id
    AND older.status = 'DRAFT' AND newer.status = 'DRAFT' AND older.id < newer.id
SET older.status = 'SUPERSEDED';

-- 仅填充旧草稿缺失的基准版本；已有基准不重写，避免掩盖版本冲突。
UPDATE t_course_content_revision draft
LEFT JOIN (
    SELECT course_id, MAX(publish_version) AS latest FROM
        (SELECT course_id, publish_version FROM t_course_content_revision WHERE status = 'PUBLISHED') published
    GROUP BY course_id
) base ON draft.course_id = base.course_id
SET draft.publish_version = COALESCE(base.latest, 0)
WHERE draft.status = 'DRAFT' AND draft.publish_version IS NULL;
