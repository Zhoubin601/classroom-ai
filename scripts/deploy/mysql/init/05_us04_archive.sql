-- 增量迁移：不推断归属，不虚构旧归档时间或操作者。重复执行安全。
SET @migration_sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_course_offering' AND column_name='archived_at')=0, 'ALTER TABLE t_course_offering ADD COLUMN archived_at DATETIME(6) NULL', 'SELECT 1');
PREPARE archive_migration FROM @migration_sql;
EXECUTE archive_migration;
DEALLOCATE PREPARE archive_migration;
SET @migration_sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_course_offering' AND column_name='archived_by')=0, 'ALTER TABLE t_course_offering ADD COLUMN archived_by VARCHAR(64) NULL', 'SELECT 1');
PREPARE archive_migration FROM @migration_sql;
EXECUTE archive_migration;
DEALLOCATE PREPARE archive_migration;
SET @migration_sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_course_offering' AND column_name='history_snapshot')=0, 'ALTER TABLE t_course_offering ADD COLUMN history_snapshot LONGTEXT NULL', 'SELECT 1');
PREPARE archive_migration FROM @migration_sql;
EXECUTE archive_migration;
DEALLOCATE PREPARE archive_migration;

-- 待核对清单（仅查询，不改写）：未知专业、教师关系缺失、历史快照不完整。
SELECT o.id, o.academic_term, o.class_name, o.major_id, o.major_code,
  CASE WHEN COALESCE(o.major_code,c.major_code,'')='' THEN '专业归属缺失'
       WHEN NOT EXISTS (SELECT 1 FROM t_course_offering_teacher t WHERE t.offering_id=o.id) THEN '任课关联缺失'
       WHEN o.is_snapshot_frozen=1 AND (o.snapshot_student_count IS NULL OR o.history_snapshot IS NULL) THEN '旧归档快照待核对'
       ELSE '专业 ID 或编码不一致' END AS review_reason
FROM t_course_offering o JOIN t_course c ON c.id=o.course_id
WHERE COALESCE(o.major_code,c.major_code,'')=''
   OR NOT EXISTS (SELECT 1 FROM t_course_offering_teacher t WHERE t.offering_id=o.id)
   OR (o.is_snapshot_frozen=1 AND (o.snapshot_student_count IS NULL OR o.history_snapshot IS NULL))
   OR (o.major_id IS NOT NULL AND c.major_id IS NOT NULL AND o.major_id<>c.major_id)
   OR (o.major_code IS NOT NULL AND c.major_code IS NOT NULL AND o.major_code<>c.major_code);
