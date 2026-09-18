-- ==========================================================
-- 具身机器人智能课堂分析系统 - 数据库初始化脚本
-- 字符集: utf8mb4 / 排序规则: utf8mb4_unicode_ci
-- ==========================================================

CREATE DATABASE IF NOT EXISTS `classroom_ai` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `classroom_ai`;

-- 1. 学生档案基础信息表
CREATE TABLE IF NOT EXISTS `student` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `student_id` VARCHAR(64) NOT NULL COMMENT '学号（唯一标识）',
    `name` VARCHAR(64) NOT NULL COMMENT '学生姓名',
    `gender` VARCHAR(16) DEFAULT 'UNKNOWN' COMMENT '性别: MALE, FEMALE, UNKNOWN',
    `class_name` VARCHAR(64) DEFAULT NULL COMMENT '所属班级',
    `avatar_url` VARCHAR(255) DEFAULT NULL COMMENT '人脸底库/头像展示URL',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '录入时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_student_id` (`student_id`),
    KEY `idx_class_name` (`class_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生基础档案表';

-- 2. 人脸特征向量表 (存储 InsightFace 512维 Float 数组)
CREATE TABLE IF NOT EXISTS `face_feature` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `student_id` VARCHAR(64) NOT NULL COMMENT '关联学号',
    `feature_dim` INT NOT NULL DEFAULT 512 COMMENT '特征维度（InsightFace默认为512）',
    `feature_vector` MEDIUMTEXT NOT NULL COMMENT '512维特征向量(JSON Float数组)',
    `image_path` VARCHAR(255) DEFAULT NULL COMMENT '特征采集图片本地路径',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '特征创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_student_id` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人脸高维特征向量表';

-- 3. 课堂会话及宏观分析记录表 (供课后回溯与历史报表)
CREATE TABLE IF NOT EXISTS `classroom_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `session_id` VARCHAR(64) NOT NULL COMMENT '课堂会话标识',
    `course_name` VARCHAR(64) DEFAULT '智能课堂分析' COMMENT '课程名称',
    `class_name` VARCHAR(64) DEFAULT NULL COMMENT '授课班级',
    `total_expected` INT NOT NULL DEFAULT 0 COMMENT '应到人数',
    `actual_present` INT NOT NULL DEFAULT 0 COMMENT '实到人数',
    `attendance_rate` DOUBLE NOT NULL DEFAULT 0.0 COMMENT '出勤率 (0.0~1.0)',
    `lookup_rate` DOUBLE NOT NULL DEFAULT 0.0 COMMENT '抬头率 (0.0~1.0)',
    `look_down_count` INT NOT NULL DEFAULT 0 COMMENT '低头/异常人数',
    `record_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上报时间戳',
    PRIMARY KEY (`id`),
    KEY `idx_session_id` (`session_id`),
    KEY `idx_record_time` (`record_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课堂时序宏观记录表';
