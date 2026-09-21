-- ==========================================================
-- 实验二 Sprint 1 课程底座版本化数据库变更与主数据初始化
-- 对应代号蜂鸟·阶段 B
-- ==========================================================

USE `classroom_ai`;

-- 1. 专业独立字典表
CREATE TABLE IF NOT EXISTS `t_major` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `major_code` VARCHAR(32) NOT NULL COMMENT '专业编码，如 SE, CS, AI',
    `major_name` VARCHAR(64) NOT NULL COMMENT '专业名称',
    `department` VARCHAR(64) DEFAULT NULL COMMENT '所属学院/教研室',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_major_code` (`major_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专业字典表';

-- 2. 教师主数据表
CREATE TABLE IF NOT EXISTS `t_teacher` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `teacher_code` VARCHAR(32) NOT NULL COMMENT '教师工号',
    `teacher_name` VARCHAR(64) NOT NULL COMMENT '教师姓名',
    `department` VARCHAR(64) DEFAULT NULL COMMENT '所属教研室',
    `title` VARCHAR(32) DEFAULT NULL COMMENT '职称',
    `user_id` BIGINT DEFAULT NULL COMMENT '关联用户账号ID',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_teacher_code` (`teacher_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教师主数据档案表';

-- 3. 本地用户账号表
CREATE TABLE IF NOT EXISTS `t_user_account` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `username` VARCHAR(64) NOT NULL COMMENT '登录账号',
    `password` VARCHAR(128) NOT NULL COMMENT '登录密码',
    `real_name` VARCHAR(64) NOT NULL COMMENT '真实姓名',
    `role` VARCHAR(32) NOT NULL COMMENT '角色: DIRECTOR, TEACHER, SUPERVISOR',
    `department` VARCHAR(64) DEFAULT NULL COMMENT '所属教研室',
    `teacher_code` VARCHAR(32) DEFAULT NULL COMMENT '关联教师工号',
    `authorized_majors` VARCHAR(255) DEFAULT NULL COMMENT '督导授权专业(分号分隔)',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户账号表';

-- 4. 开课班次与多教师联合开课关联表
CREATE TABLE IF NOT EXISTS `t_course_offering_teacher` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `offering_id` BIGINT NOT NULL COMMENT '班次ID',
    `teacher_id` BIGINT NOT NULL COMMENT '教师ID',
    `teacher_code` VARCHAR(32) DEFAULT NULL COMMENT '教师工号',
    `teacher_name` VARCHAR(64) NOT NULL COMMENT '教师姓名',
    `role_in_offering` VARCHAR(32) DEFAULT 'PRIMARY' COMMENT '分工: PRIMARY, ASSISTANT',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `idx_offering_teacher` (`offering_id`, `teacher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班次多教师授课关联表';

-- 5. 班次—学生选课名单真值表
CREATE TABLE IF NOT EXISTS `t_offering_student_enrollment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `offering_id` BIGINT NOT NULL COMMENT '开课班次ID',
    `student_id` BIGINT DEFAULT NULL COMMENT '学生ID',
    `student_number` VARCHAR(64) NOT NULL COMMENT '学号',
    `student_name` VARCHAR(64) NOT NULL COMMENT '学生姓名',
    `admin_class_name` VARCHAR(64) DEFAULT NULL COMMENT '行政班级',
    `enrolled_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_offering_student` (`offering_id`, `student_number`),
    KEY `idx_offering_id` (`offering_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班次学生选课名单表';

-- 6. 课程档案表 (US-01 规范底座)
CREATE TABLE IF NOT EXISTS `t_course` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '课程主键ID',
    `course_code` VARCHAR(64) NOT NULL COMMENT '课程代码(唯一标识)',
    `course_name` VARCHAR(128) NOT NULL COMMENT '课程名称',
    `department` VARCHAR(64) DEFAULT NULL COMMENT '开课院系/教研室',
    `teacher_name` VARCHAR(64) DEFAULT NULL COMMENT '主讲/任课教师姓名',
    `major_id` BIGINT DEFAULT NULL COMMENT '所属专业ID',
    `major_code` VARCHAR(32) DEFAULT NULL COMMENT '所属专业编码(如 SE, CS, AI, DS, SEC)',
    `credits` DOUBLE NOT NULL COMMENT '学分',
    `hours` INT NOT NULL COMMENT '总学时',
    `theory_hours` INT DEFAULT NULL COMMENT '理论学时',
    `practice_hours` INT DEFAULT NULL COMMENT '实验/实践学时',
    `course_type` VARCHAR(32) DEFAULT NULL COMMENT '课程性质',
    `prerequisites` VARCHAR(255) DEFAULT NULL COMMENT '先修课程关系',
    `description` TEXT DEFAULT NULL COMMENT '课程简介 (US-02)',
    `objectives` TEXT DEFAULT NULL COMMENT '教学目标 (US-02)',
    `assessment_method` TEXT DEFAULT NULL COMMENT '考核方式 (US-02)',
    `created_at` DATETIME(6) DEFAULT NULL COMMENT '创建时间',
    `updated_at` DATETIME(6) DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_course_code` (`course_code`),
    KEY `idx_course_major_code` (`major_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程全量档案底座表';

-- 7. 课程内容大纲与简介版本控制表 (US-02 多版本追溯与乐观锁控制)
CREATE TABLE IF NOT EXISTS `t_course_content_revision` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '版本主键ID',
    `course_id` BIGINT NOT NULL COMMENT '关联课程ID',
    `description` TEXT DEFAULT NULL COMMENT '课程简介',
    `objectives` TEXT DEFAULT NULL COMMENT '教学目标',
    `assessment_method` TEXT DEFAULT NULL COMMENT '考核方式',
    `version` INT NOT NULL COMMENT '兼容版本号',
    `publish_version` INT DEFAULT NULL COMMENT '正式发布版本号(v1, v2...)',
    `lock_version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `status` VARCHAR(32) NOT NULL COMMENT '状态: DRAFT, PUBLISHED, ARCHIVED',
    `editor_name` VARCHAR(64) DEFAULT NULL COMMENT '最后编辑人',
    `publisher_code` VARCHAR(32) DEFAULT NULL COMMENT '发布教师工号',
    `publisher_name` VARCHAR(64) DEFAULT NULL COMMENT '发布教师姓名',
    `published_at` DATETIME(6) DEFAULT NULL COMMENT '发布时间',
    `created_at` DATETIME(6) DEFAULT NULL COMMENT '创建时间',
    `updated_at` DATETIME(6) DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_course_version` (`course_id`, `version`),
    KEY `idx_course_status` (`course_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程简介与大纲版本控制表';

-- 8. 课程批量导入审计记录表 (US-01 规范导入日志)
CREATE TABLE IF NOT EXISTS `t_course_import_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志主键ID',
    `batch_id` VARCHAR(64) NOT NULL COMMENT '导入批次号UUID',
    `operator` VARCHAR(64) NOT NULL COMMENT '操作人用户名',
    `file_name` VARCHAR(255) DEFAULT NULL COMMENT '上传原始文件名',
    `total_rows` INT DEFAULT NULL COMMENT '总解析行数',
    `success_count` INT DEFAULT NULL COMMENT '成功导入行数',
    `error_count` INT DEFAULT NULL COMMENT '校验失败行数',
    `status` VARCHAR(32) NOT NULL COMMENT '导入状态: PREVIEW, COMMITTED, REJECTED, EXPIRED',
    `message` VARCHAR(512) DEFAULT NULL COMMENT '执行摘要说明',
    `created_at` DATETIME(6) DEFAULT NULL COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_import_batch_id` (`batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程CSV批量导入审计记录表';
