package com.classroom.ai.modules.course.controller;

import com.classroom.ai.common.ApiResponse;
import com.classroom.ai.modules.auth.context.AuthContext;
import com.classroom.ai.modules.auth.entity.RoleEnum;
import com.classroom.ai.modules.auth.vo.UserVO;
import com.classroom.ai.modules.course.entity.CourseOffering;
import com.classroom.ai.modules.course.entity.CourseOfferingTeacher;
import com.classroom.ai.modules.course.entity.CourseSchedule;
import com.classroom.ai.modules.course.repository.CourseOfferingRepository;
import com.classroom.ai.modules.course.repository.CourseOfferingTeacherRepository;
import com.classroom.ai.modules.course.repository.CourseScheduleRepository;
import com.classroom.ai.modules.course.repository.OfferingStudentEnrollmentRepository;
import com.classroom.ai.modules.course.service.CourseAuthorizationService;
import com.classroom.ai.modules.course.vo.OfferingHistoryItemVO;
import com.classroom.ai.modules.course.vo.OfferingHistoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/courses/offerings")
@RequiredArgsConstructor
@CrossOrigin
public class CourseOfferingHistoryController {

    private final CourseOfferingRepository offeringRepository;
    private final CourseOfferingTeacherRepository offeringTeacherRepository;
    private final CourseScheduleRepository scheduleRepository;
    private final OfferingStudentEnrollmentRepository enrollmentRepository;
    private final CourseAuthorizationService authorizationService;

    /**
     * US-04 历史开课与学生人数/累计人次统计
     * 口径：
     * - 单班次选课人数：在读由选课名单统计，已结课取固化快照
     * - 累计人次：筛选范围内各班次选课人数累加 (同一班次多教师不重复计算)
     * - 权限控制由 CourseAuthorizationService 统一纳管 (主任管辖教研室、教师本人关联、督导授权专业)
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    @GetMapping("/history")
    public ApiResponse<OfferingHistoryVO> getOfferingHistory(@RequestParam(required = false) String term) {
        authorizationService.requireCurrentUser();
        List<CourseOffering> offerings;
        if (term != null && !term.trim().isEmpty()) {
            offerings = offeringRepository.findByAcademicTerm(term.trim());
        } else {
            offerings = offeringRepository.findAll();
        }

        // 统一数据权限过滤 (未登录自动抛出 401，各角色统一授权口径)
        offerings = authorizationService.filterOfferings(offerings);

        if (offerings.isEmpty()) {
            return ApiResponse.success(OfferingHistoryVO.builder()
                    .term(term)
                    .totalOfferings(0)
                    .cumulativePersonTimes(0)
                    .items(Collections.emptyList())
                    .build());
        }

        List<OfferingHistoryItemVO> items = new ArrayList<>();
        int cumulativePersonTimes = 0;

        for (CourseOffering off : offerings) {
            boolean frozen = Boolean.TRUE.equals(off.getIsSnapshotFrozen());
            if (frozen && off.getSnapshotStudentCount() == null) throw new IllegalStateException("归档班次缺少人数快照，请核对历史迁移数据");
            int count = frozen ? off.getSnapshotStudentCount() : Math.toIntExact(enrollmentRepository.countByOfferingId(off.getId()));
            cumulativePersonTimes += count;
            OfferingHistoryItemVO item;
            if (frozen && off.getHistorySnapshot() != null) {
                item = com.alibaba.fastjson2.JSON.parseObject(off.getHistorySnapshot(), OfferingHistoryItemVO.class);
            } else {
                item = com.classroom.ai.modules.course.service.OfferingHistorySnapshot.build(off,
                    offeringTeacherRepository.findByOfferingId(off.getId()), scheduleRepository.findByOfferingId(off.getId()), count);
            }
            items.add(item);
        }

        OfferingHistoryVO vo = OfferingHistoryVO.builder()
                .term(term)
                .totalOfferings(items.size())
                .cumulativePersonTimes(cumulativePersonTimes)
                .items(items)
                .build();

        return ApiResponse.success(vo);
    }
}
