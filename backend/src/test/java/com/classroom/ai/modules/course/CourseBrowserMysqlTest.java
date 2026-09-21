package com.classroom.ai.modules.course;

import com.classroom.ai.common.*;
import com.classroom.ai.modules.auth.context.AuthContext;
import com.classroom.ai.modules.auth.entity.RoleEnum;
import com.classroom.ai.modules.auth.vo.UserVO;
import com.classroom.ai.modules.course.controller.*;
import com.classroom.ai.modules.course.dto.CourseOfferingDTO;
import com.classroom.ai.modules.course.entity.*;
import com.classroom.ai.modules.course.repository.*;
import com.classroom.ai.modules.course.service.*;
import com.classroom.ai.modules.course.service.impl.*;
import com.classroom.ai.repository.StudentRepository;
import com.classroom.ai.entity.Student;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.*;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP controllers + MySQL + two Chromium contexts. Includes real login UI and the production JWT security chain. */
@SpringBootTest(classes=CourseBrowserMysqlTest.Config.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.sql.init.mode=never","spring.jpa.open-in-view=false"})
@EnabledIfEnvironmentVariable(named="US0203_BROWSER",matches="true")
class CourseBrowserMysqlTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) { CourseWorkflowMysqlTest.database(r); }
    @Configuration
    @EnableAutoConfiguration(exclude={SecurityAutoConfiguration.class,UserDetailsServiceAutoConfiguration.class})
    @EntityScan("com.classroom.ai")
    @EnableJpaRepositories("com.classroom.ai")
    @Import({com.classroom.ai.modules.auth.security.SecurityConfig.class,com.classroom.ai.modules.auth.security.JwtAuthenticationFilter.class,com.classroom.ai.modules.auth.security.JwtTokenProvider.class,com.classroom.ai.modules.auth.controller.AuthController.class,com.classroom.ai.modules.auth.controller.DirectorController.class,MajorController.class,CourseOfferingHistoryController.class,CourseOfferingQueryController.class,CourseOfferingQueryService.class,CourseImportController.class,CourseContentController.class,CourseOfferingController.class,CourseScheduleController.class,CourseController.class,
        CourseAuthorizationService.class,CourseServiceImpl.class,CourseImportServiceImpl.class,CourseOfferingManagementService.class,
        CourseScheduleServiceImpl.class,ScheduleConflictService.class,AcademicTermLockService.class,ApiExceptionHandler.class,Fixtures.class})
    static class Config {}
    @RestController
    static class Fixtures {
        @Autowired TeacherRepository teachers;
        @Autowired StudentRepository students;
        @GetMapping("/api/v1/teachers") Object teachers() {return ApiResponse.success(teachers.findAll());}
        @GetMapping("/api/student/list") Object students() {return ApiResponse.success(students.findAll());}
        @GetMapping({"/api/v1/syllabus/course/{id}/indicators"}) Object empty() {return ApiResponse.success(List.of());}
    }
    @Autowired MajorRepository majors;
    @Autowired com.classroom.ai.modules.auth.repository.UserAccountRepository accounts;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;
    @Autowired CourseRepository courses;
    @Autowired CourseOfferingRepository offerings;
    @Autowired TeacherRepository teachers;
    @Autowired StudentRepository students;
    @Autowired CourseOfferingManagementService management;
    @LocalServerPort int port;
    @Test void realBrowserWorkflow() throws Exception {
        String password = UUID.randomUUID().toString();
        for (RoleEnum role : RoleEnum.values()) accounts.saveAndFlush(com.classroom.ai.modules.auth.entity.UserAccount.builder().username("browser_"+role.name()).password(encoder.encode(password)).realName("测试教师").role(role).department("Browser Dept").teacherCode(role==RoleEnum.TEACHER?"BROWSER-T1":null).authorizedMajors(role==RoleEnum.SUPERVISOR?"SE":null).build());
        var major=majors.saveAndFlush(Major.builder().majorCode("SE").majorName("软件工程").department("Browser Dept").build());
        majors.saveAndFlush(Major.builder().majorCode("AI").majorName("人工智能").department("Other Dept").build());
        var course=courses.saveAndFlush(Course.builder().courseCode("BROWSER").courseName("软件项目管理").department("Browser Dept").majorCode("SE").majorId(major.getId()).credits(3.0).hours(48).courseType("core").build());
        var t1=teachers.saveAndFlush(Teacher.builder().teacherCode("BROWSER-T1").teacherName("测试教师").department("Browser Dept").build());
        teachers.saveAndFlush(Teacher.builder().teacherCode("BROWSER-T2").teacherName("协同教师").department("Browser Dept").build());
        List<String> numbers=new ArrayList<>();
        for(int i=0;i<95;i++){String number="B"+i;numbers.add(number);students.save(Student.builder().studentId(number).name("学生"+i).className("样例行政班").build());} students.flush();
        AuthContext.setCurrentUser(UserVO.builder().role(RoleEnum.DIRECTOR).department("Browser Dept").build());
        try {var d=new CourseOfferingDTO();d.setCourseId(course.getId());d.setAcademicTerm("2026秋季");d.setClassName("样例教学班");d.setPrimaryTeacherId(t1.getId());d.setCollaboratingTeacherIds(List.of());d.setStudentNumbers(numbers);management.save(null,d);} finally {AuthContext.clear();}
        var outside=courses.saveAndFlush(Course.builder().courseCode("OUTSIDE").courseName("未授权课程").department("Other Dept").majorCode("AI").majorId(2L).credits(1.0).hours(16).courseType("core").build());
        var outsideOffering=offerings.saveAndFlush(CourseOffering.builder().course(outside).academicTerm("2026秋季").className("未授权教学班").teacherName("其他教师").studentCount(0).majorId(2L).majorCode("AI").build());
        Path root=Path.of(System.getProperty("user.dir")).toAbsolutePath(); if(root.getFileName().toString().equals("backend"))root=root.getParent();
        ProcessBuilder builder=new ProcessBuilder("node",root.resolve("scripts/tests/us02-us03-real-browser.cjs").toString());
        builder.environment().put("US0203_TEST_PASSWORD",password);
        builder.environment().put("US0203_BACKEND_URL","http://127.0.0.1:"+port);
        builder.environment().put("US0203_COURSE_ID",course.getId().toString());
        builder.environment().put("US0406_OUTSIDE_COURSE_ID",outside.getId().toString());
        builder.environment().put("US0406_OUTSIDE_OFFERING_ID",outsideOffering.getId().toString());
        builder.redirectErrorStream(true).redirectOutput(root.resolve("docs/us0203-real-browser.log").toFile());
        Process process=builder.start();
        try {assertTrue(process.waitFor(150,TimeUnit.SECONDS),"浏览器验收超时");assertEquals(0,process.exitValue(),Files.readString(root.resolve("docs/us0203-real-browser.log")));}
        finally {if(process.isAlive())process.destroyForcibly();}
    }
}
