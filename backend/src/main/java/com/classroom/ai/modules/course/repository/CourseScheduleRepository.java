package com.classroom.ai.modules.course.repository;

import com.classroom.ai.modules.course.entity.CourseSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface CourseScheduleRepository extends JpaRepository<CourseSchedule, Long> {

    List<CourseSchedule> findByOfferingId(Long offeringId);

    List<CourseSchedule> findByClassroom(String classroom);

    List<CourseSchedule> findByDayOfWeek(Integer dayOfWeek);

    /**
     * 教室冲突检测：同一学期、同一教室、周次重叠、同一星期几、时段重叠
     */
    @Query("SELECT s FROM CourseSchedule s WHERE " +
           "(:academicTerm IS NULL OR s.offering.academicTerm = :academicTerm) AND " +
           "s.classroom = :classroom AND " +
           "s.dayOfWeek = :dayOfWeek AND " +
           "(:excludeScheduleId IS NULL OR s.id != :excludeScheduleId) AND " +
           "(s.startWeek <= :endWeek AND s.endWeek >= :startWeek) AND " +
           "(s.startPeriod <= :endPeriod AND s.endPeriod >= :startPeriod)")
    List<CourseSchedule> findConflictingClassroomSchedules(
            @Param("academicTerm") String academicTerm,
            @Param("classroom") String classroom,
            @Param("dayOfWeek") Integer dayOfWeek,
            @Param("startWeek") Integer startWeek,
            @Param("endWeek") Integer endWeek,
            @Param("startPeriod") Integer startPeriod,
            @Param("endPeriod") Integer endPeriod,
            @Param("excludeScheduleId") Long excludeScheduleId
    );

    /**
     * 教师冲突检测：同一学期、任一授课教师开设的班次、周次重叠、同一星期几、时段重叠
     */
    @Query("SELECT s FROM CourseSchedule s WHERE " +
           "(:academicTerm IS NULL OR s.offering.academicTerm = :academicTerm) AND " +
           "s.offering.id IN :offeringIds AND " +
           "s.dayOfWeek = :dayOfWeek AND " +
           "(:excludeScheduleId IS NULL OR s.id != :excludeScheduleId) AND " +
           "(s.startWeek <= :endWeek AND s.endWeek >= :startWeek) AND " +
           "(s.startPeriod <= :endPeriod AND s.endPeriod >= :startPeriod)")
    List<CourseSchedule> findConflictingTeacherSchedules(
            @Param("academicTerm") String academicTerm,
            @Param("offeringIds") Collection<Long> offeringIds,
            @Param("dayOfWeek") Integer dayOfWeek,
            @Param("startWeek") Integer startWeek,
            @Param("endWeek") Integer endWeek,
            @Param("startPeriod") Integer startPeriod,
            @Param("endPeriod") Integer endPeriod,
            @Param("excludeScheduleId") Long excludeScheduleId
    );

    /**
     * 保持向后兼容的旧检测接口
     */
    default List<CourseSchedule> findConflictingSchedules(
            String classroom, Integer dayOfWeek, Integer startWeek, Integer endWeek,
            Integer startPeriod, Integer endPeriod, Long excludeScheduleId) {
        return findConflictingClassroomSchedules(null, classroom, dayOfWeek, startWeek, endWeek, startPeriod, endPeriod, excludeScheduleId);
    }
}
