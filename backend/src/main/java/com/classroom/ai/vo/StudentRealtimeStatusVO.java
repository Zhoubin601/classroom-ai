package com.classroom.ai.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentRealtimeStatusVO implements Serializable {

    private String studentId;

    private String name;

    private String className;

    private String avatarUrl;

    // 是否在场出勤
    private Boolean present;

    // 当前姿态: "UP" (抬头听课), "DOWN" (低头/走神), "ABSENT" (缺勤)
    private String poseState;

    // 最近出现时间戳描述
    private String lastSeenTime;
}
