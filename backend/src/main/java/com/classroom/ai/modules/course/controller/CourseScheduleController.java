package com.classroom.ai.modules.course.controller;

import com.classroom.ai.common.ApiResponse;
import com.classroom.ai.modules.course.dto.CourseScheduleDTO;
import com.classroom.ai.modules.course.entity.CourseSchedule;
import com.classroom.ai.modules.course.service.CourseAuthorizationService;
import com.classroom.ai.modules.course.service.CourseScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
@CrossOrigin
public class CourseScheduleController {

    private final CourseScheduleService scheduleService;
    private final com.classroom.ai.modules.course.repository.CourseOfferingRepository offeringRepository;
    private final CourseAuthorizationService authorizationService;

    @GetMapping
    public ApiResponse<List<CourseSchedule>> getAllSchedules(@RequestParam(required = false) Long offeringId,
                                                             @RequestParam(required = false) String classroom,
                                                             @RequestParam(required = false) String term,
                                                             @RequestParam(required = false) String teacher,
                                                             @RequestParam(required = false) Integer week) {
        if (offeringId != null) {
            offeringRepository.findById(offeringId).ifPresent(authorizationService::validateOfferingRead);
        }
        List<CourseSchedule> list;
        if (term != null || teacher != null || week != null || offeringId != null || classroom != null) {
            list = scheduleService.getFilteredSchedules(term, teacher, week, classroom, offeringId);
        } else {
            list = scheduleService.getAllSchedules();
        }
        return ApiResponse.success(list);
    }

    @PostMapping
    public ApiResponse<CourseSchedule> saveSchedule(@RequestBody CourseScheduleDTO dto) {
        return ApiResponse.success("排课成功", scheduleService.saveSchedule(dto));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteSchedule(@PathVariable Long id) {
        scheduleService.deleteSchedule(id);
        return ApiResponse.success("排课记录删除成功", null);
    }

    @GetMapping("/check-conflict")
    public ApiResponse<List<CourseSchedule>> checkConflict(@RequestParam String classroom,
                                                           @RequestParam Integer dayOfWeek,
                                                           @RequestParam(defaultValue = "1") Integer startWeek,
                                                           @RequestParam(defaultValue = "16") Integer endWeek,
                                                           @RequestParam Integer startPeriod,
                                                           @RequestParam Integer endPeriod,
                                                           @RequestParam(required = false) Long offeringId,
                                                           @RequestParam(required = false) Long excludeId) {
        List<CourseSchedule> conflicts = scheduleService.checkConflict(offeringId, classroom, dayOfWeek, startWeek, endWeek, startPeriod, endPeriod, excludeId);
        return ApiResponse.success(conflicts);
    }
}
