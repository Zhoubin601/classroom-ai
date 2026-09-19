package com.classroom.ai.service.impl;

import com.alibaba.fastjson2.JSON;
import com.classroom.ai.dto.ClassroomStreamDTO;
import com.classroom.ai.entity.ClassroomRecord;
import com.classroom.ai.entity.Student;
import com.classroom.ai.modules.attendance.repository.AttendanceSessionRepository;
import com.classroom.ai.modules.course.entity.CourseOffering;
import com.classroom.ai.modules.course.entity.OfferingStudentEnrollment;
import com.classroom.ai.modules.course.repository.CourseOfferingRepository;
import com.classroom.ai.modules.course.repository.OfferingStudentEnrollmentRepository;
import com.classroom.ai.repository.ClassroomRecordRepository;
import com.classroom.ai.repository.StudentRepository;
import com.classroom.ai.service.VisualDashboardService;
import com.classroom.ai.vo.DashboardOverviewVO;
import com.classroom.ai.vo.FocusTrendPointVO;
import com.classroom.ai.vo.StudentRealtimeStatusVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class VisualDashboardServiceImpl implements VisualDashboardService {

    private final StudentRepository studentRepository;
    private final ClassroomRecordRepository classroomRecordRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final CourseOfferingRepository courseOfferingRepository;
    private final OfferingStudentEnrollmentRepository enrollmentRepository;
    private final AttendanceSessionRepository attendanceSessionRepository;

    @Autowired
    public VisualDashboardServiceImpl(StudentRepository studentRepository,
                                       ClassroomRecordRepository classroomRecordRepository,
                                       StringRedisTemplate stringRedisTemplate,
                                       CourseOfferingRepository courseOfferingRepository,
                                       OfferingStudentEnrollmentRepository enrollmentRepository,
                                       AttendanceSessionRepository attendanceSessionRepository) {
        this.studentRepository = studentRepository;
        this.classroomRecordRepository = classroomRecordRepository;
        this.stringRedisTemplate = stringRedisTemplate;
        this.courseOfferingRepository = courseOfferingRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.attendanceSessionRepository = attendanceSessionRepository;
    }

    public VisualDashboardServiceImpl(StudentRepository studentRepository,
                                       ClassroomRecordRepository classroomRecordRepository,
                                       StringRedisTemplate stringRedisTemplate,
                                       CourseOfferingRepository courseOfferingRepository) {
        this(studentRepository, classroomRecordRepository, stringRedisTemplate, courseOfferingRepository, null, null);
    }

    private static final String KEY_REALTIME_OVERVIEW = "classroom:realtime:overview";
    private static final String KEY_REALTIME_POSES = "classroom:realtime:poses";
    private static final String KEY_TREND_HISTORY = "classroom:trend:history";
    private static final String KEY_PRESENT_IDS = "classroom:realtime:present_ids";
    private static final String KEY_AUDITING_IDS = "classroom:realtime:auditing_ids";
    private static final String KEY_LAST_HEARTBEAT = "classroom:realtime:last_heartbeat";
    private static final String KEY_ACTIVE_OFFERING_ID = "classroom:realtime:offering_id";

    // 12秒内无推断流上报，判定为待机/未推流
    private static final long STREAM_MAX_STALENESS_MS = 12000L;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private boolean isStreamActive(Long offeringId) {
        try {
            String heartbeatStr = stringRedisTemplate.opsForValue().get(KEY_LAST_HEARTBEAT);
            if (heartbeatStr == null || heartbeatStr.isBlank()) {
                return false;
            }
            long lastHeartbeat = Long.parseLong(heartbeatStr);
            if (System.currentTimeMillis() - lastHeartbeat > STREAM_MAX_STALENESS_MS) {
                return false;
            }
            if (offeringId != null) {
                String activeOffIdStr = stringRedisTemplate.opsForValue().get(KEY_ACTIVE_OFFERING_ID);
                if (activeOffIdStr != null && !activeOffIdStr.isBlank()) {
                    long activeOffId = Long.parseLong(activeOffIdStr);
                    if (activeOffId != offeringId) {
                        return false;
                    }
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

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

        // 1. 确定当前推断流归属的开课班次 (offeringId)
        Long offeringId = streamDTO.getOfferingId();
        if (offeringId == null && streamDTO.getClassName() != null) {
            List<CourseOffering> offList = courseOfferingRepository.findByClassName(streamDTO.getClassName());
            if (offList != null && !offList.isEmpty()) {
                offeringId = offList.get(0).getId();
            }
        }
        if (offeringId == null && attendanceSessionRepository != null) {
            var activeSessionOpt = attendanceSessionRepository.findFirstByStatusOrderByCreatedAtDesc("ACTIVE");
            if (activeSessionOpt.isPresent() && activeSessionOpt.get().getOffering() != null) {
                offeringId = activeSessionOpt.get().getOffering().getId();
            }
        }

        // 2. 获取本班正式选课学生学号集合 (以班次选课名单为唯一基准真值)
        Set<String> enrolledStudentIds = new LinkedHashSet<>();
        if (offeringId != null) {
            List<OfferingStudentEnrollment> enrollments = enrollmentRepository != null ? enrollmentRepository.findByOfferingId(offeringId) : Collections.emptyList();
            if (enrollments != null && !enrollments.isEmpty()) {
                for (OfferingStudentEnrollment en : enrollments) {
                    enrolledStudentIds.add(en.getStudentNumber());
                }
            }
        }
        if (enrolledStudentIds.isEmpty() && offeringId == null && studentRepository != null) {
            for (Student s : studentRepository.findAll()) {
                enrolledStudentIds.add(s.getStudentId());
            }
        }
        // 如果未配置开课或在隔离测试环境中名单为空，将当前去重后的识别学生作为基准
        if (enrolledStudentIds.isEmpty() && (offeringId == null || enrollmentRepository == null)) {
            List<String> raw = streamDTO.getPresentStudentIds() != null ? streamDTO.getPresentStudentIds() : Collections.emptyList();
            enrolledStudentIds.addAll(new HashSet<>(raw));
        }

        // 3. 严格人脸识别过滤：将识别到的学号严格区分为【本班出勤】与【非本班/旁听/未知】
        List<String> rawPresentIds = streamDTO.getPresentStudentIds() != null ? streamDTO.getPresentStudentIds() : Collections.emptyList();
        Set<String> uniquePresent = new HashSet<>(rawPresentIds);

        Set<String> enrolledPresentIds = new LinkedHashSet<>();
        Set<String> auditingIds = new LinkedHashSet<>();

        for (String sid : uniquePresent) {
            if (enrolledStudentIds.contains(sid)) {
                enrolledPresentIds.add(sid);
            } else {
                // 非本班学生 / 外系学生 / 未知人脸
                auditingIds.add(sid);
            }
        }

        // 4. 计算官方出勤指标（严格仅限本班选课学生，非本班学生绝对不计入本班出勤率！）
        int totalRegistered = enrolledStudentIds.size();
        int presentCount = enrolledPresentIds.size();
        int auditingCount = auditingIds.size();
        int absentCount = Math.max(0, totalRegistered - presentCount);
        double attendanceRate = totalRegistered > 0 ? Math.round(((double) presentCount / totalRegistered * 100.0) * 10.0) / 10.0 : 0.0;

        double lookupRate = streamDTO.getLookupRate() != null ? streamDTO.getLookupRate() * 100.0 : 0.0;
        int lookdownCount = streamDTO.getLookdownCount() != null ? streamDTO.getLookdownCount() : 0;

        // 专注度综合评级
        String focusLevel = "良好 (Normal)";
        if (lookupRate >= 85.0) {
            focusLevel = "优秀 (High)";
        } else if (lookupRate < 60.0) {
            focusLevel = "需关注 (Low)";
        }

        // 5. 更新概览 VO 到 Redis，并设置严格 TTL (20秒) 避免产生陈旧残留数据
        DashboardOverviewVO overviewVO = DashboardOverviewVO.builder()
                .totalRegistered(totalRegistered)
                .currentPresent(presentCount)
                .currentAbsent(absentCount)
                .attendanceRate(attendanceRate)
                .realtimeLookupRate(Math.round(lookupRate * 10.0) / 10.0)
                .lookdownCount(lookdownCount)
                .focusLevel(focusLevel)
                .auditingCount(auditingCount)
                .auditingStudentIds(new ArrayList<>(auditingIds))
                .lastUpdateTime(dateTimeStr)
                .build();

        try {
            stringRedisTemplate.opsForValue().set(KEY_REALTIME_OVERVIEW, JSON.toJSONString(overviewVO), 20, TimeUnit.SECONDS);
            stringRedisTemplate.opsForValue().set(KEY_LAST_HEARTBEAT, String.valueOf(System.currentTimeMillis()), 20, TimeUnit.SECONDS);
            if (offeringId != null) {
                stringRedisTemplate.opsForValue().set(KEY_ACTIVE_OFFERING_ID, String.valueOf(offeringId), 20, TimeUnit.SECONDS);
            }

            // 更新本班出勤学生 ID 集合 (带 TTL)
            stringRedisTemplate.delete(KEY_PRESENT_IDS);
            if (!enrolledPresentIds.isEmpty()) {
                stringRedisTemplate.opsForSet().add(KEY_PRESENT_IDS, enrolledPresentIds.toArray(new String[0]));
                stringRedisTemplate.expire(KEY_PRESENT_IDS, 20, TimeUnit.SECONDS);
            }

            // 更新非本班旁听学生 ID 集合 (带 TTL)
            stringRedisTemplate.delete(KEY_AUDITING_IDS);
            if (!auditingIds.isEmpty()) {
                stringRedisTemplate.opsForSet().add(KEY_AUDITING_IDS, auditingIds.toArray(new String[0]));
                stringRedisTemplate.expire(KEY_AUDITING_IDS, 20, TimeUnit.SECONDS);
            }

            // 更新学生姿态状态 Hash (带 TTL)
            stringRedisTemplate.delete(KEY_REALTIME_POSES);
            if (streamDTO.getStudentPoses() != null && !streamDTO.getStudentPoses().isEmpty()) {
                stringRedisTemplate.opsForHash().putAll(KEY_REALTIME_POSES, streamDTO.getStudentPoses());
                stringRedisTemplate.expire(KEY_REALTIME_POSES, 20, TimeUnit.SECONDS);
            }

            // 追加时序折线数据点 (限制最近 60 个点)
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
            stringRedisTemplate.expire(KEY_TREND_HISTORY, 60, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Failed to update realtime cache in Redis: {}", e.getMessage());
        }

        // 6. 持久化一条宏观记录至 MySQL
        try {
            ClassroomRecord record = ClassroomRecord.builder()
                    .sessionId(streamDTO.getSessionId() != null ? streamDTO.getSessionId() : "SESSION_" + now.toLocalDate())
                    .courseName(streamDTO.getCourseName() != null ? streamDTO.getCourseName() : "智能课堂分析")
                    .className(streamDTO.getClassName() != null ? streamDTO.getClassName() : "软件工程班级")
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
            long enrolledCount = enrollmentRepository != null ? enrollmentRepository.countByOfferingId(offeringId) : 0;
            if (enrolledCount > 0) {
                total = (int) enrolledCount;
            } else {
                var offOpt = courseOfferingRepository.findById(offeringId);
                if (offOpt.isPresent()) {
                    total = (int) studentRepository.countByClassName(offOpt.get().getClassName());
                }
            }
        }
        if (total == 0 && studentRepository != null) {
            total = (int) studentRepository.count();
        }

        // 核心时效性校验：如果摄像头未开启或最近 12 秒内没有收到有效推断心跳，强制返回干净初始待机状态
        if (!isStreamActive(offeringId)) {
            return DashboardOverviewVO.builder()
                    .totalRegistered(total)
                    .currentPresent(0)
                    .currentAbsent(total)
                    .attendanceRate(0.0)
                    .realtimeLookupRate(0.0)
                    .lookdownCount(0)
                    .focusLevel("待机就绪 / 未开课")
                    .auditingCount(0)
                    .auditingStudentIds(Collections.emptyList())
                    .lastUpdateTime(LocalDateTime.now().format(DATE_TIME_FORMATTER))
                    .build();
        }

        // 若推断流处于活跃期，从 Redis 读取最新帧指标
        try {
            String json = stringRedisTemplate.opsForValue().get(KEY_REALTIME_OVERVIEW);
            if (json != null && !json.isBlank()) {
                DashboardOverviewVO vo = JSON.parseObject(json, DashboardOverviewVO.class);
                if (vo != null) {
                    vo.setTotalRegistered(total);
                    vo.setCurrentAbsent(Math.max(0, total - (vo.getCurrentPresent() != null ? vo.getCurrentPresent() : 0)));
                    if (total > 0 && vo.getCurrentPresent() != null) {
                        vo.setAttendanceRate(Math.round(((double) vo.getCurrentPresent() / total * 100.0) * 10.0) / 10.0);
                    }
                    if (vo.getAuditingCount() == null) {
                        vo.setAuditingCount(0);
                    }
                    return vo;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch overview from Redis: {}", e.getMessage());
        }

        return DashboardOverviewVO.builder()
                .totalRegistered(total)
                .currentPresent(0)
                .currentAbsent(total)
                .attendanceRate(0.0)
                .realtimeLookupRate(0.0)
                .lookdownCount(0)
                .focusLevel("待机就绪 / 未开课")
                .auditingCount(0)
                .auditingStudentIds(Collections.emptyList())
                .lastUpdateTime(LocalDateTime.now().format(DATE_TIME_FORMATTER))
                .build();
    }

    @Override
    public List<FocusTrendPointVO> getTrend() {
        if (!isStreamActive(null)) {
            String nowStr = LocalDateTime.now().format(TIME_FORMATTER);
            return List.of(FocusTrendPointVO.builder().time(nowStr).lookupRate(0.0).presentCount(0).build());
        }

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
        List<Student> students = new ArrayList<>();
        if (offeringId != null) {
            List<OfferingStudentEnrollment> enrollments = enrollmentRepository != null ? enrollmentRepository.findByOfferingId(offeringId) : Collections.emptyList();
            if (enrollments != null && !enrollments.isEmpty()) {
                List<String> studentNumbers = enrollments.stream().map(OfferingStudentEnrollment::getStudentNumber).toList();
                students = studentRepository != null ? studentRepository.findByStudentIdIn(studentNumbers) : Collections.emptyList();
            }
        } else {
            students = studentRepository != null ? studentRepository.findAll() : Collections.emptyList();
        }

        List<StudentRealtimeStatusVO> result = new ArrayList<>();
        String nowStr = LocalDateTime.now().format(TIME_FORMATTER);

        // 如果摄像头未推流或心跳已过期，所有正式学生直接显示缺勤/待机状态，且无旁听学生
        if (!isStreamActive(offeringId)) {
            for (Student s : students) {
                result.add(StudentRealtimeStatusVO.builder()
                        .studentId(s.getStudentId())
                        .name(s.getName())
                        .className(s.getClassName())
                        .avatarUrl(s.getAvatarUrl())
                        .present(false)
                        .poseState("ABSENT")
                        .isAuditing(false)
                        .lastSeenTime("未出勤")
                        .build());
            }
            return result;
        }

        // 推断流活跃：读取本班出勤与非本班旁听学生
        Set<String> presentIds = Collections.emptySet();
        Set<String> auditingIds = Collections.emptySet();
        Map<Object, Object> posesMap = Collections.emptyMap();

        try {
            Set<String> members = stringRedisTemplate.opsForSet().members(KEY_PRESENT_IDS);
            if (members != null) presentIds = members;

            Set<String> auditMembers = stringRedisTemplate.opsForSet().members(KEY_AUDITING_IDS);
            if (auditMembers != null) auditingIds = auditMembers;

            posesMap = stringRedisTemplate.opsForHash().entries(KEY_REALTIME_POSES);
        } catch (Exception e) {
            log.warn("Failed to fetch present ids / poses from Redis: {}", e.getMessage());
        }

        // 1. 装载本班学生 (无论出勤与否)
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
                    .isAuditing(false)
                    .lastSeenTime(isPresent ? nowStr : "未出勤")
                    .build());
        }

        // 2. 装载非本班学生/旁听学生 (独立标明 isAuditing=true，供前端专属展示)
        for (String aid : auditingIds) {
            Optional<Student> studentOpt = studentRepository.findByStudentId(aid);
            String name = studentOpt.map(s -> s.getName() + " (非本班)").orElse("未知学生 (" + aid + ")");
            String className = studentOpt.map(Student::getClassName).orElse("非本班/旁听");
            String avatar = studentOpt.map(Student::getAvatarUrl).orElse("https://api.dicebear.com/7.x/bottts/svg?seed=" + aid);

            Object poseVal = posesMap.get(aid);
            String pose = poseVal != null ? poseVal.toString() : "UP";

            result.add(StudentRealtimeStatusVO.builder()
                    .studentId(aid)
                    .name(name)
                    .className(className)
                    .avatarUrl(avatar)
                    .present(true)
                    .poseState(pose)
                    .isAuditing(true)
                    .lastSeenTime(nowStr)
                    .build());
        }

        return result;
    }

    @Override
    public void clearRealtimeStreamData() {
        clearRealtimeStreamData(null);
    }

    @Override
    public void clearRealtimeStreamData(Long offeringId) {
        try {
            stringRedisTemplate.delete(List.of(
                    KEY_REALTIME_OVERVIEW,
                    KEY_REALTIME_POSES,
                    KEY_TREND_HISTORY,
                    KEY_PRESENT_IDS,
                    KEY_AUDITING_IDS,
                    KEY_LAST_HEARTBEAT,
                    KEY_ACTIVE_OFFERING_ID
            ));
            log.info("【爱教学】实时大屏推断缓存已彻底清理并归零复位 (offeringId={})", offeringId);
        } catch (Exception e) {
            log.warn("Failed to clear realtime stream data in Redis: {}", e.getMessage());
        }
    }
}
