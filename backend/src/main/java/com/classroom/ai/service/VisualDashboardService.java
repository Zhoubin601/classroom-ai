package com.classroom.ai.service;

import com.classroom.ai.dto.ClassroomStreamDTO;
import com.classroom.ai.vo.DashboardOverviewVO;
import com.classroom.ai.vo.FocusTrendPointVO;
import com.classroom.ai.vo.StudentRealtimeStatusVO;

import java.util.List;

public interface VisualDashboardService {

    /**
     * 处理 Python 视觉端/机器人上报的课堂单帧/周期推断流数据
     */
    void processClassroomStream(ClassroomStreamDTO streamDTO);

    /**
     * 获取大屏实时宏观看板指标（出勤率、抬头率、预警数）
     */
    DashboardOverviewVO getOverview();
    DashboardOverviewVO getOverview(Long offeringId);

    /**
     * 获取 ECharts 抬头率与专注度时序趋势折线数据
     */
    List<FocusTrendPointVO> getTrend();

    /**
     * 获取当前班级/所有学生的实时考勤与姿态状态明细卡片
     */
    List<StudentRealtimeStatusVO> getStudentsRealtimeStatus();
    List<StudentRealtimeStatusVO> getStudentsRealtimeStatus(Long offeringId);
}
