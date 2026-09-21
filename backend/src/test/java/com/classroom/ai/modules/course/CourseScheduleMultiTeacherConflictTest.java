package com.classroom.ai.modules.course;
import com.classroom.ai.modules.course.entity.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CourseScheduleMultiTeacherConflictTest {
 final CourseScheduleConflictTest fixture = new CourseScheduleConflictTest();
 final com.classroom.ai.modules.course.repository.CourseScheduleRepository rows = fixture.rows;
 final com.classroom.ai.modules.course.repository.CourseOfferingTeacherRepository teachers = fixture.teachers;
 CourseOffering offering(long id,String term,String code) {return fixture.offering(id,term,code);}
 CourseSchedule row(long id,CourseOffering offering,String room) {return fixture.row(id,offering,room);}
 List<CourseSchedule> find(CourseOffering offering,String room,Long exclude) {return fixture.find(offering,room,exclude);}

 @Test void collaboratorAcrossClassroomsBlocksAndIdentifiesTeacher() {
  var a=offering(1,"T","T1"); var b=offering(2,"T","T2");
  when(teachers.findByOfferingId(2L)).thenReturn(List.of(CourseOfferingTeacher.builder().teacherId(1L).teacherCode("T1").teacherName("郭军").roleInOffering("ASSISTANT").build()));
  when(rows.findAll()).thenReturn(List.of(row(1,a,"信息馆 B201")));
  var found=find(b,"文管 A447",null);
  assertEquals(1,found.size()); assertTrue(found.get(0).getConflictReasons().get(0).contains("T1"));
 }
 @Test void semesterMoveAlsoChecksInternalOfferingOverlap() {
  var original=offering(1,"OLD","T1"); var candidate=offering(1,"NEW","T2");
  when(rows.findAll()).thenReturn(List.of(row(1,original,"A"),row(2,original,"B")));
  var found=find(candidate,"A",1L);
  assertEquals(1,found.size()); assertEquals(2L,found.get(0).getId());
 }
}
