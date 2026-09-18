package com.classroom.ai.modules.course.repository;

import com.classroom.ai.modules.course.entity.CourseSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseScheduleRepository extends JpaRepository<CourseSchedule, Long> {

    List<CourseSchedule> findByOfferingId(Long offeringId);

    List<CourseSchedule> findByClassroom(String classroom);

    List<CourseSchedule> findByDayOfWeek(Integer dayOfWeek);

    /**
     * 排课冲突检测：查找同一教室、周次重叠、同一星期几、时段重叠的排课记录
     */
    @Query("SELECT s FROM CourseSchedule s WHERE " +
           "s.classroom = :classroom AND " +
           "s.dayOfWeek = :dayOfWeek AND " +
           "(:excludeScheduleId IS NULL OR s.id != :excludeScheduleId) AND " +
           "((s.startWeek <= :endWeek) AND (s.endWeek >= :startWeek)) AND " +
           "((s.startPeriod <= :endPeriod) AND (s.endPeriod >= :startPeriod))")
    List<CourseSchedule> findConflictingSchedules(
            @Param("classroom") String classroom,
            @Param("dayOfWeek") Integer dayOfWeek,
            @Param("startWeek") Integer startWeek,
            @Param("endWeek") Integer endWeek,
            @Param("startPeriod") Integer startPeriod,
            @Param("endPeriod") Integer endPeriod,
            @Param("excludeScheduleId") Long excludeScheduleId
    );
}
