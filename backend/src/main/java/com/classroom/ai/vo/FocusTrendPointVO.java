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
public class FocusTrendPointVO implements Serializable {

    // 时间刻度，如 "14:20:05"
    private String time;

    // 抬头率百分比 (0.0 ~ 100.0)
    private Double lookupRate;

    // 当前在座实到人数
    private Integer presentCount;
}
