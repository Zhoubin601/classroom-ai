package com.classroom.ai.modules.course.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfferingHistoryVO {
    private String term;
    private int totalOfferings;
    private int cumulativePersonTimes; // 累计人次 (各班次人数之和)
    private List<OfferingHistoryItemVO> items;
}
