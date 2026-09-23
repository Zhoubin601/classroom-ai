-- Sprint 2 additive migration; safe to run repeatedly on an existing volume.
CREATE TABLE IF NOT EXISTS t_training_indicator (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  major_code VARCHAR(32) NOT NULL,
  plan_version VARCHAR(32) NOT NULL,
  indicator_code VARCHAR(32) NOT NULL,
  requirement_category VARCHAR(64) NOT NULL,
  indicator_description TEXT NULL,
  UNIQUE KEY uk_training_indicator (major_code, plan_version, indicator_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_course_syllabus' AND column_name='plan_version')=0,
 'ALTER TABLE t_course_syllabus ADD COLUMN plan_version VARCHAR(32) NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
UPDATE t_course_syllabus SET plan_version=version WHERE plan_version IS NULL;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_course_resource' AND column_name='tags')=0,
 'ALTER TABLE t_course_resource ADD COLUMN tags VARCHAR(255) NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
UPDATE t_course_resource SET tags=IF(tag='未标注','',tag) WHERE tags IS NULL;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_supervision_evaluation' AND column_name='supervisor_user_id')=0,
 'ALTER TABLE t_supervision_evaluation ADD COLUMN supervisor_user_id BIGINT NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_supervision_evaluation' AND column_name='reviewed_by')=0,
 'ALTER TABLE t_supervision_evaluation ADD COLUMN reviewed_by BIGINT NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_supervision_evaluation' AND column_name='reviewed_at')=0,
 'ALTER TABLE t_supervision_evaluation ADD COLUMN reviewed_at DATETIME(6) NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_supervision_evaluation' AND column_name='review_note')=0,
 'ALTER TABLE t_supervision_evaluation ADD COLUMN review_note VARCHAR(500) NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
UPDATE t_supervision_evaluation SET status='PENDING_REVIEW', publish_time=NULL WHERE status='PENDING_DESENSITIZE';
-- Legacy published rows have no director approval record. Preserve them for review,
-- and do not show them to teachers until this Sprint 2 workflow is completed.
UPDATE t_supervision_evaluation SET status='PENDING_REVIEW', publish_time=NULL
WHERE status='PUBLISHED' AND reviewed_by IS NULL;

CREATE TABLE IF NOT EXISTS t_resource_access_log (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  resource_id BIGINT NOT NULL,
  viewer_id BIGINT NOT NULL,
  viewer_username VARCHAR(64) NOT NULL,
  action VARCHAR(16) NOT NULL,
  accessed_at DATETIME(6) NOT NULL,
  KEY idx_resource_access_resource (resource_id, accessed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS t_evaluation_audit_log (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  evaluation_id BIGINT NOT NULL,
  actor_id BIGINT NOT NULL,
  actor_username VARCHAR(64) NOT NULL,
  action VARCHAR(32) NOT NULL,
  from_status VARCHAR(32) NULL,
  to_status VARCHAR(32) NOT NULL,
  occurred_at DATETIME(6) NOT NULL,
  KEY idx_eval_audit_evaluation (evaluation_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Existing mappings become a legacy catalogue for their actual major/version.
INSERT IGNORE INTO t_training_indicator (major_code, plan_version, indicator_code, requirement_category, indicator_description)
SELECT c.major_code, COALESCE(s.plan_version, 'legacy'), i.indicator_code,
       MIN(i.requirement_category), MIN(i.indicator_description)
FROM t_graduation_indicator i
JOIN t_course c ON c.id=i.course_id
LEFT JOIN t_course_syllabus s ON s.id=i.syllabus_id
WHERE c.major_code IS NOT NULL AND c.major_code<>''
GROUP BY c.major_code, COALESCE(s.plan_version, 'legacy'), i.indicator_code;
