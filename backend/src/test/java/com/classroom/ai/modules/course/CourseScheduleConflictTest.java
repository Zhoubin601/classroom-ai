package com.classroom.ai.modules.course;
import com.classroom.ai.modules.course.entity.*;
import com.classroom.ai.modules.course.repository.*;
import com.classroom.ai.modules.course.service.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CourseScheduleConflictTest {
 final CourseScheduleRepository rows = mock(CourseScheduleRepository.class);
 final CourseOfferingTeacherRepository teachers = mock(CourseOfferingTeacherRepository.class);
 final ScheduleConflictService service = new ScheduleConflictService(rows, teachers);
 CourseOffering offering(long id, String term, String code) {
  return CourseOffering.builder().id(id).academicTerm(term).teacherCode(code).teacherName("同名教师").course(Course.builder().courseName("测试课程").build()).studentCount(95).build();
 }
 CourseSchedule row(long id, CourseOffering o, String room) {
  return CourseSchedule.builder().id(id).offering(o).classroom(room).dayOfWeek(3).startWeek(1).endWeek(16).startPeriod(3).endPeriod(4).build();
 }
 List<CourseSchedule> find(CourseOffering o, String room, Long exclude) {
  return service.find(o,service.teacherCodes(o),room,3,1,16,3,4,exclude);
 }
 @Test void classroomConflictReturnsAllRowsAndReasons() {
  var o = offering(1,"T","T1");
  when(rows.findAll()).thenReturn(List.of(row(1,offering(2,"T","T2"),"文管 A447"),row(2,offering(3,"T","T3"),"文管A447")));
  var found = find(o,"文管 a447",null);
  assertEquals(2,found.size()); assertEquals(List.of("教室冲突"),found.get(0).getConflictReasons());
  assertEquals(95,found.get(0).getOffering().getStudentCount());
 }
 @Test void sameOfferingDifferentClassroomStillConflicts() {
  var o = offering(1,"T","T1"); when(rows.findAll()).thenReturn(List.of(row(1,o,"A")));
  assertTrue(find(o,"B",null).get(0).getConflictReasons().get(0).contains("T1"));
  assertTrue(find(o,"A",1L).isEmpty());
 }
 @Test void semesterSeparationAndNamesAreNotTeacherIdentity() {
  var o = offering(1,"T","T1");
  when(rows.findAll()).thenReturn(List.of(row(1,offering(2,"OTHER","T1"),"A"),row(2,offering(3,"T","T2"),"B")));
  assertTrue(find(o,"A",null).isEmpty());
 }
 @Test void weeksAndPeriodsOverlapInclusively() {
  var o=offering(1,"T","T1"); when(rows.findAll()).thenReturn(List.of(row(1,o,"A")));
  assertEquals(1,service.find(o,Set.of("T1"),"B",3,16,20,4,5,null).size());
  assertTrue(service.find(o,Set.of("T1"),"A",3,17,20,3,4,null).isEmpty());
  assertTrue(service.find(o,Set.of("T1"),"A",3,1,16,5,6,null).isEmpty());
 }
}
