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
