package com.classroom.ai.controller;

import com.classroom.ai.common.ApiResponse;
import com.classroom.ai.dto.ClassroomStreamDTO;
import com.classroom.ai.service.VisualDashboardService;
import com.classroom.ai.vo.DashboardOverviewVO;
import com.classroom.ai.vo.FocusTrendPointVO;
import com.classroom.ai.vo.StudentRealtimeStatusVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/visual")
@RequiredArgsConstructor
public class VisualDashboardController {

    private final VisualDashboardService visualDashboardService;

    /**
     * 1. 获取大屏实时宏观看板数据（出勤率、抬头率、预警数、专注评级）
     */
    @GetMapping("/overview")
    public ApiResponse<DashboardOverviewVO> getOverview(@RequestParam(required = false) Long offeringId) {
        DashboardOverviewVO overview = visualDashboardService.getOverview(offeringId);
        return ApiResponse.success(overview);
    }

    /**
     * 2. 获取 ECharts 抬头率与专注度时序曲线数据
     */
    @GetMapping("/trend")
    public ApiResponse<List<FocusTrendPointVO>> getTrend() {
        List<FocusTrendPointVO> trend = visualDashboardService.getTrend();
        return ApiResponse.success(trend);
    }

    /**
     * 3. 获取班级所有学生的实时出勤与姿态明细（网格卡片展示）
     */
    @GetMapping("/students/status")
    public ApiResponse<List<StudentRealtimeStatusVO>> getStudentsRealtimeStatus(@RequestParam(required = false) Long offeringId) {
        List<StudentRealtimeStatusVO> list = visualDashboardService.getStudentsRealtimeStatus(offeringId);
        return ApiResponse.success(list);
    }

    /**
     * 4. 接收 Python 边缘视觉端/机器人上报的单帧/周期推断流数据
     */
    @PostMapping("/report/stream")
    public ApiResponse<String> reportStream(@RequestBody ClassroomStreamDTO streamDTO) {
        visualDashboardService.processClassroomStream(streamDTO);
        return ApiResponse.success("Stream metric processed successfully", "OK");
    }

    /**
     * 5. 主动清空推断大屏缓存数据（停止推流、下课或切换班级）
     */
    @PostMapping("/reset")
    public ApiResponse<String> resetStream(@RequestParam(required = false) Long offeringId) {
        visualDashboardService.clearRealtimeStreamData(offeringId);
        return ApiResponse.success("Realtime stream cache reset successfully", "OK");
    }
}
