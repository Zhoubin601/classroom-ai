-- One-time migration of explicit demo course associations. New courses never grant scope.
CREATE TABLE IF NOT EXISTS t_major_department (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    major_id BIGINT NOT NULL,
    department VARCHAR(64) NOT NULL,
    CONSTRAINT uk_major_department UNIQUE (major_id, department),
    CONSTRAINT fk_major_department_major FOREIGN KEY (major_id) REFERENCES t_major (id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS t_app_data_migration (
    version VARCHAR(96) NOT NULL PRIMARY KEY,
    applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO t_major_department (major_id, department)
SELECT DISTINCT m.id, c.department
FROM t_major m JOIN t_course c
    ON m.major_code COLLATE utf8mb4_unicode_ci = c.major_code COLLATE utf8mb4_unicode_ci
WHERE m.major_code = 'CS'
  AND ((c.course_code IN ('CS2001', 'CS1002') AND c.department = '基础软件教研室')
    OR (c.course_code IN ('CS3002', 'CS3008') AND c.department = '系统软件教研室'))
  AND NOT EXISTS (SELECT 1 FROM t_app_data_migration WHERE version = 'major-department-v1')
  AND NOT EXISTS (SELECT 1 FROM t_major_department d
      WHERE d.major_id = m.id
        AND d.department COLLATE utf8mb4_unicode_ci = c.department COLLATE utf8mb4_unicode_ci);

INSERT INTO t_app_data_migration (version)
SELECT 'major-department-v1'
WHERE NOT EXISTS (SELECT 1 FROM t_app_data_migration WHERE version = 'major-department-v1');
