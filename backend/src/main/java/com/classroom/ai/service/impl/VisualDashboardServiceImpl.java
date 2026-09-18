package com.classroom.ai.service.impl;

import com.alibaba.fastjson2.JSON;
import com.classroom.ai.dto.ClassroomStreamDTO;
import com.classroom.ai.entity.ClassroomRecord;
import com.classroom.ai.entity.Student;
import com.classroom.ai.repository.ClassroomRecordRepository;
import com.classroom.ai.repository.StudentRepository;
import com.classroom.ai.service.VisualDashboardService;
import com.classroom.ai.vo.DashboardOverviewVO;
import com.classroom.ai.vo.FocusTrendPointVO;
import com.classroom.ai.vo.StudentRealtimeStatusVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class VisualDashboardServiceImpl implements VisualDashboardService {

    private final StudentRepository studentRepository;
    private final ClassroomRecordRepository classroomRecordRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final com.classroom.ai.modules.course.repository.CourseOfferingRepository courseOfferingRepository;

    private static final String KEY_REALTIME_OVERVIEW = "classroom:realtime:overview";
    private static final String KEY_REALTIME_POSES = "classroom:realtime:poses";
    private static final String KEY_TREND_HISTORY = "classroom:trend:history";
    private static final String KEY_PRESENT_IDS = "classroom:realtime:present_ids";

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void processClassroomStream(ClassroomStreamDTO streamDTO) {
        if (streamDTO == null) return;
        if (streamDTO.getLookupRate() != null && (!Double.isFinite(streamDTO.getLookupRate())
                || streamDTO.getLookupRate() < 0 || streamDTO.getLookupRate() > 1)) {
            throw new IllegalArgumentException("lookupRate must be between 0 and 1");
        }

        LocalDateTime now = LocalDateTime.now();
        String timeStr = now.format(TIME_FORMATTER);
        String dateTimeStr = now.format(DATE_TIME_FORMATTER);

        int totalRegistered = (int) studentRepository.count();
        int presentCount = streamDTO.getPresentStudentIds() != null ? new HashSet<>(streamDTO.getPresentStudentIds()).size()
                : (streamDTO.getDetectedPersonCount() != null ? streamDTO.getDetectedPersonCount() : 0);

        int absentCount = Math.max(0, totalRegistered - presentCount);
        double attendanceRate = totalRegistered > 0 ? (double) presentCount / totalRegistered * 100.0 : 0.0;

        double lookupRate = streamDTO.getLookupRate() != null ? streamDTO.getLookupRate() * 100.0 : 0.0;
        int lookdownCount = streamDTO.getLookdownCount() != null ? streamDTO.getLookdownCount() : 0;

        // 专注度综合评级
        String focusLevel = "良好 (Normal)";
        if (lookupRate >= 85.0) {
            focusLevel = "优秀 (High)";
        } else if (lookupRate < 60.0) {
            focusLevel = "需关注 (Low)";
        }

        // 1. 更新概览 VO 到 Redis
        DashboardOverviewVO overviewVO = DashboardOverviewVO.builder()
                .totalRegistered(totalRegistered)
                .currentPresent(presentCount)
                .currentAbsent(absentCount)
                .attendanceRate(Math.round(attendanceRate * 10.0) / 10.0)
                .realtimeLookupRate(Math.round(lookupRate * 10.0) / 10.0)
                .lookdownCount(lookdownCount)
                .focusLevel(focusLevel)
                .lastUpdateTime(dateTimeStr)
                .build();

        try {
            stringRedisTemplate.opsForValue().set(KEY_REALTIME_OVERVIEW, JSON.toJSONString(overviewVO));

            // 2. 更新出勤学生 ID 集合
            stringRedisTemplate.delete(KEY_PRESENT_IDS);
            if (streamDTO.getPresentStudentIds() != null && !streamDTO.getPresentStudentIds().isEmpty()) {
                stringRedisTemplate.opsForSet().add(KEY_PRESENT_IDS,
                        streamDTO.getPresentStudentIds().toArray(new String[0]));
            }

            // 3. 更新学生姿态状态 Hash
            stringRedisTemplate.delete(KEY_REALTIME_POSES);
            if (streamDTO.getStudentPoses() != null && !streamDTO.getStudentPoses().isEmpty()) {
                stringRedisTemplate.opsForHash().putAll(KEY_REALTIME_POSES, streamDTO.getStudentPoses());
            }

            // 4. 追加时序折线数据点 (限制最近 60 个点)
            FocusTrendPointVO trendPoint = FocusTrendPointVO.builder()
                    .time(timeStr)
                    .lookupRate(Math.round(lookupRate * 10.0) / 10.0)
                    .presentCount(presentCount)
                    .build();

            stringRedisTemplate.opsForList().rightPush(KEY_TREND_HISTORY, JSON.toJSONString(trendPoint));
            Long listSize = stringRedisTemplate.opsForList().size(KEY_TREND_HISTORY);
            if (listSize != null && listSize > 60) {
                stringRedisTemplate.opsForList().leftPop(KEY_TREND_HISTORY);
            }
        } catch (Exception e) {
            log.warn("Failed to update realtime cache in Redis: {}", e.getMessage());
        }

        // 5. 异步/定时持久化一条宏观记录至 MySQL
        try {
            ClassroomRecord record = ClassroomRecord.builder()
                    .sessionId(streamDTO.getSessionId() != null ? streamDTO.getSessionId() : "SESSION_" + now.toLocalDate())
                    .courseName(streamDTO.getCourseName() != null ? streamDTO.getCourseName() : "智能课堂分析")
                    .className(streamDTO.getClassName() != null ? streamDTO.getClassName() : "高一(1)班")
                    .totalExpected(totalRegistered)
                    .actualPresent(presentCount)
                    .attendanceRate(attendanceRate / 100.0)
                    .lookupRate(lookupRate / 100.0)
                    .lookDownCount(lookdownCount)
                    .build();
            classroomRecordRepository.save(record);
        } catch (Exception e) {
            log.error("Failed to save classroom record to MySQL: {}", e.getMessage());
        }
    }

    @Override
    public DashboardOverviewVO getOverview() {
        return getOverview(null);
    }

    @Override
    public DashboardOverviewVO getOverview(Long offeringId) {
        int total = 0;
        if (offeringId != null) {
            var offOpt = courseOfferingRepository.findById(offeringId);
            if (offOpt.isPresent()) {
                total = (int) studentRepository.countByClassName(offOpt.get().getClassName());
            }
        }
        if (total == 0) {
            total = (int) studentRepository.count();
        }

        // 先从 Redis 读取实时流指标
        try {
            String json = stringRedisTemplate.opsForValue().get(KEY_REALTIME_OVERVIEW);
            if (json != null && !json.isBlank()) {
                DashboardOverviewVO vo = JSON.parseObject(json, DashboardOverviewVO.class);
                if (vo != null) {
                    // 确保应到人数始终以 MySQL student 表为唯一基准真值来源
                    vo.setTotalRegistered(total);
                    vo.setCurrentAbsent(Math.max(0, total - vo.getCurrentPresent()));
                    if (total > 0) {
                        vo.setAttendanceRate(Math.round(((double) vo.getCurrentPresent() / total * 100.0) * 10.0) / 10.0);
                    }
                    return vo;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch overview from Redis: {}", e.getMessage());
        }

        // 如果没有实时流，根据本地 MySQL 数据生成默认真实概览
        return DashboardOverviewVO.builder()
                .totalRegistered(total)
                .currentPresent(0)
                .currentAbsent(total)
                .attendanceRate(0.0)
                .realtimeLookupRate(0.0)
                .lookdownCount(0)
                .focusLevel("未开课 / 等待感知流")
                .lastUpdateTime(LocalDateTime.now().format(DATE_TIME_FORMATTER))
                .build();
    }

    @Override
    public List<FocusTrendPointVO> getTrend() {
        List<FocusTrendPointVO> list = new ArrayList<>();
        try {
            List<String> rawList = stringRedisTemplate.opsForList().range(KEY_TREND_HISTORY, 0, -1);
            if (rawList != null) {
                for (String s : rawList) {
                    list.add(JSON.parseObject(s, FocusTrendPointVO.class));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch trend from Redis: {}", e.getMessage());
        }

        if (list.isEmpty()) {
            // 提供占位时间点，确保前端 ECharts 立即有坐标轴和空曲线
            String nowStr = LocalDateTime.now().format(TIME_FORMATTER);
            list.add(FocusTrendPointVO.builder().time(nowStr).lookupRate(0.0).presentCount(0).build());
        }
        return list;
    }

    @Override
    public List<StudentRealtimeStatusVO> getStudentsRealtimeStatus() {
        return getStudentsRealtimeStatus(null);
    }

    @Override
    public List<StudentRealtimeStatusVO> getStudentsRealtimeStatus(Long offeringId) {
        List<Student> students;
        if (offeringId != null) {
            var offOpt = courseOfferingRepository.findById(offeringId);
            if (offOpt.isPresent() && offOpt.get().getClassName() != null) {
                students = studentRepository.findByClassName(offOpt.get().getClassName());
            } else {
                students = studentRepository.findAll();
            }
        } else {
            students = studentRepository.findAll();
        }

        List<StudentRealtimeStatusVO> result = new ArrayList<>();

        Set<String> presentIds = Collections.emptySet();
        Map<Object, Object> posesMap = Collections.emptyMap();

        try {
            Set<String> members = stringRedisTemplate.opsForSet().members(KEY_PRESENT_IDS);
            if (members != null) {
                presentIds = members;
            }
            posesMap = stringRedisTemplate.opsForHash().entries(KEY_REALTIME_POSES);
        } catch (Exception e) {
            log.warn("Failed to fetch present ids / poses from Redis: {}", e.getMessage());
        }

        String nowStr = LocalDateTime.now().format(TIME_FORMATTER);

        for (Student s : students) {
            boolean isPresent = presentIds.contains(s.getStudentId());
            String pose = "ABSENT";
            if (isPresent) {
                Object poseVal = posesMap.get(s.getStudentId());
                pose = poseVal != null ? poseVal.toString() : "UP";
            }

            result.add(StudentRealtimeStatusVO.builder()
                    .studentId(s.getStudentId())
                    .name(s.getName())
                    .className(s.getClassName())
                    .avatarUrl(s.getAvatarUrl())
                    .present(isPresent)
                    .poseState(pose)
                    .lastSeenTime(isPresent ? nowStr : "未出勤")
                    .build());
        }

        return result;
    }
}
