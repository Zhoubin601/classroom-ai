package com.classroom.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassroomStreamDTO implements Serializable {

    // 课堂会话ID
    private String sessionId;

    // 开课班次ID (CourseOffering ID)
    private Long offeringId;

    // 课程名称
    private String courseName;

    // 班级名称
    private String className;

    // 目标检测出的人体/总在座人数
    private Integer detectedPersonCount;

    // 抬头人数
    private Integer lookupCount;

    // 低头人数
    private Integer lookdownCount;

    // 当前帧抬头率 (0.0 ~ 1.0)
    private Double lookupRate;

    // 识别到出勤的学生学号列表
    private List<String> presentStudentIds;

    // 每个学生当前的姿态状态：学号 -> "UP" / "DOWN"
    private Map<String, String> studentPoses;
}
