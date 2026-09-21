package com.classroom.ai.modules.course.service;

import com.classroom.ai.modules.course.dto.CourseOfferingDTO;
import com.classroom.ai.modules.course.entity.*;
import com.classroom.ai.modules.course.repository.*;
import com.classroom.ai.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(isolation = Isolation.READ_COMMITTED)
public class CourseOfferingManagementService {
    private final CourseOfferingRepository offerings;
    private final CourseRepository courses;
    private final TeacherRepository teachers;
    private final CourseOfferingTeacherRepository relations;
    private final OfferingStudentEnrollmentRepository enrollments;
    private final StudentRepository students;
    private final CourseScheduleRepository schedules;
    private final AcademicTermLockService locks;
    private final ScheduleConflictService conflicts;
    private final CourseAuthorizationService authorization;

    public Map<String,Object> details(Long id) {
        CourseOffering o = offerings.findById(id).orElseThrow(() -> new IllegalArgumentException("班次不存在"));
        authorization.validateOfferingRead(o);
        var roster = enrollments.findByOfferingId(id);
        return Map.of("offering", conflicts.hydrate(o), "studentNumbers", roster.stream().map(OfferingStudentEnrollment::getStudentNumber).toList(), "studentIds", roster.stream().map(OfferingStudentEnrollment::getStudentId).filter(Objects::nonNull).toList());
    }

    private CourseOffering locked(Long id) {
        CourseOffering o = offerings.findForUpdate(id).orElseThrow(() -> new IllegalArgumentException("班次不存在"));
        CourseArchiveRules.validateDepartment(o.getCourse().getDepartment());
        if (Boolean.TRUE.equals(o.getIsSnapshotFrozen())) throw new IllegalStateException("历史班次已冻结");
        return o;
    }

    public CourseOffering save(Long id, CourseOfferingDTO dto) {
        CourseArchiveRules.requireDirector();
        // Canonical IDs take precedence over legacy code/list inputs.
        if (dto.getTeacherIds() != null) {
            var ids = dto.getTeacherIds();
            if (dto.getPrimaryTeacherId() == null || ids.stream().anyMatch(Objects::isNull) || new HashSet<>(ids).size() != ids.size() || !ids.contains(dto.getPrimaryTeacherId())) throw new IllegalArgumentException("教师 ID 列表必须唯一且包含主讲教师");
            dto.setCollaboratingTeacherIds(ids.stream().filter(t -> !t.equals(dto.getPrimaryTeacherId())).toList());
        }
        if (dto.getStudentIds() != null) {
            var ids = dto.getStudentIds();
            if (ids.stream().anyMatch(Objects::isNull) || new HashSet<>(ids).size() != ids.size()) throw new IllegalArgumentException("学生 ID 列表含空值或重复");
            var roster = students.findAllById(ids);
            if (roster.size() != ids.size()) throw new IllegalArgumentException("学生 ID 不存在");
            dto.setStudentNumbers(roster.stream().map(com.classroom.ai.entity.Student::getStudentId).toList());
        }
        if (dto.getCourseId() == null || dto.getPrimaryTeacherId() == null || dto.getCollaboratingTeacherIds() == null || dto.getStudentNumbers() == null)
            throw new IllegalArgumentException("必须提供课程、主讲教师、协同教师和选课名单（允许空名单）");
        String term = required(dto.getAcademicTerm(),32,"学期"), className = required(dto.getClassName(),64,"教学班");
        CourseOffering existing = id == null ? null : locked(id);
        Course course = courses.findById(dto.getCourseId()).orElseThrow(() -> new IllegalArgumentException("课程不存在"));
        CourseArchiveRules.validateDepartment(course.getDepartment());
        LinkedHashSet<Long> teacherIds = new LinkedHashSet<>();
        teacherIds.add(dto.getPrimaryTeacherId());
        for (Long tid : dto.getCollaboratingTeacherIds()) {
            if (tid == null || !teacherIds.add(tid)) throw new IllegalArgumentException("主讲和协同教师不能重复");
        }
        List<Teacher> selected = teacherIds.stream().map(tid -> teachers.findById(tid).orElseThrow(() -> new IllegalArgumentException("教师不存在: " + tid))).toList();
        LinkedHashSet<String> numbers = new LinkedHashSet<>(dto.getStudentNumbers());
        if (numbers.size() != dto.getStudentNumbers().size() || numbers.stream().anyMatch(n -> n == null || n.isBlank())) throw new IllegalArgumentException("选课学号为空或重复");
        var selectedStudents = students.findByStudentIdIn(new ArrayList<>(numbers));
        if (selectedStudents.size() != numbers.size()) throw new IllegalArgumentException("选课名单包含不存在的学号");
        locks.lock(existing == null ? term : existing.getAcademicTerm(), term);
        Set<String> codes = new HashSet<>(); selected.forEach(t -> codes.add(ScheduleConflictService.key(t.getTeacherCode())));
        if (existing != null) {
            CourseOffering candidate = CourseOffering.builder().id(id).academicTerm(term).build();
            Map<Long,CourseSchedule> found = new LinkedHashMap<>();
            for (CourseSchedule row : schedules.findByOfferingId(id)) {
                conflicts.find(candidate,codes,row.getClassroom(),row.getDayOfWeek(),row.getStartWeek(),row.getEndWeek(),row.getStartPeriod(),row.getEndPeriod(),row.getId())
                    .forEach(c -> found.put(c.getId(),c));
            }
            if (!found.isEmpty()) throw new ScheduleConflictException(new ArrayList<>(found.values()));
        }
        Teacher primary = selected.get(0);
        CourseOffering o = existing == null ? new CourseOffering() : existing;
        o.setCourse(course); o.setAcademicTerm(term); o.setClassName(className);
        o.setMajorCode(course.getMajorCode()); o.setMajorId(course.getMajorId());
        o.setTeacherName(primary.getTeacherName()); o.setTeacherCode(primary.getTeacherCode());
        o.setStudentCount(numbers.size());
        o = offerings.saveAndFlush(o);
        final Long offeringId = o.getId();
        relations.deleteByOfferingId(offeringId); relations.flush();
        for (Teacher teacher : selected) {
            relations.save(CourseOfferingTeacher.builder().offeringId(offeringId).teacherId(teacher.getId())
                .teacherCode(teacher.getTeacherCode()).teacherName(teacher.getTeacherName())
                .roleInOffering(teacher.getId().equals(primary.getId()) ? "PRIMARY" : "ASSISTANT").build());
        }
        enrollments.deleteByOfferingId(offeringId); enrollments.flush();
        selectedStudents.forEach(s -> enrollments.save(OfferingStudentEnrollment.builder().offeringId(offeringId).studentId(s.getId())
            .studentNumber(s.getStudentId()).studentName(s.getName()).adminClassName(s.getClassName()).build()));
        relations.flush(); enrollments.flush();
        return conflicts.hydrate(o);
    }

    public CourseOffering archive(Long id) {
        var operator = CourseArchiveRules.requireDirector();
        CourseOffering o = offerings.findForUpdate(id).orElseThrow(() -> new IllegalArgumentException("班次不存在"));
        CourseArchiveRules.validateDepartment(o.getCourse().getDepartment());
        if (Boolean.TRUE.equals(o.getIsSnapshotFrozen())) return conflicts.hydrate(o);
        locks.lock(o.getAcademicTerm());
        int count = Math.toIntExact(enrollments.countByOfferingId(id));
        o.setSnapshotStudentCount(count); o.setStudentCount(count); o.setIsSnapshotFrozen(true);
        o.setStatus("FINISHED"); o.setArchivedAt(java.time.LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS)); o.setArchivedBy(operator.getUsername());
        o.setHistorySnapshot(com.alibaba.fastjson2.JSON.toJSONString(OfferingHistorySnapshot.build(o,relations.findByOfferingId(id),schedules.findByOfferingId(id),count)));
        return conflicts.hydrate(offerings.saveAndFlush(o));
    }

    public void delete(Long id) {
        CourseArchiveRules.requireDirector();
        CourseOffering o = locked(id);
        locks.lock(o.getAcademicTerm());
        // Keep dependent records explicit rather than silently destroying teaching data.
        if (!schedules.findByOfferingId(id).isEmpty()) throw new IllegalStateException("该班次仍有排课，请先删除排课");
        relations.deleteByOfferingId(id); enrollments.deleteByOfferingId(id);
        relations.flush(); enrollments.flush(); offerings.delete(o); offerings.flush();
    }

    private static String required(String s, int max, String label) {
        if (s == null || s.isBlank() || s.trim().length() > max) throw new IllegalArgumentException(label + "不能为空且不能超过 " + max + " 字符");
        return s.trim();
    }
}
