package com.classroom.ai.modules.course;

import com.classroom.ai.common.exception.ForbiddenException;
import com.classroom.ai.modules.auth.context.AuthContext;
import com.classroom.ai.modules.auth.controller.DirectorController;
import com.classroom.ai.modules.auth.entity.RoleEnum;
import com.classroom.ai.modules.auth.vo.UserVO;
import com.classroom.ai.modules.course.controller.*;
import com.classroom.ai.modules.course.dto.*;
import com.classroom.ai.modules.course.entity.*;
import com.classroom.ai.modules.course.repository.*;
import com.classroom.ai.modules.course.service.*;
import com.classroom.ai.modules.course.service.impl.SyllabusServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import jakarta.persistence.EntityManager;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=update", "spring.sql.init.mode=never"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CourseAuthorizationService.class, SyllabusServiceImpl.class, SyllabusController.class, MajorController.class})
@EnabledIfEnvironmentVariable(named = "EXP3_MYSQL_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:13317/exp3_test.*")
class MajorDepartmentMysqlTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("EXP3_MYSQL_URL"));
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", () -> "");
    }
    @Autowired EntityManager entityManager;
    @Autowired MajorRepository majors;
    @Autowired CourseRepository courses;
    @Autowired CourseSyllabusRepository syllabi;
    @Autowired GraduationIndicatorRepository mappings;
    @Autowired CourseAuthorizationService auth;
    @Autowired SyllabusServiceImpl syllabusService;
    @Autowired SyllabusController plans;
    @Autowired MajorController majorController;

    @BeforeEach void setup() {
        var major = majors.saveAndFlush(Major.builder().majorCode("MD-CS").majorName("合成测试专业").department("MD-Lead").build());
        entityManager.persist(MajorDepartment.builder().major(major).department("MD-Base").build());
        entityManager.persist(MajorDepartment.builder().major(major).department("MD-System").build());
        entityManager.flush();
    }
    @AfterEach void cleanup() { AuthContext.clear(); }
    private void user(RoleEnum role, String department) {
        AuthContext.setCurrentUser(UserVO.builder().id(1L).username("md-test").realName("测试主任")
                .role(role).department(department).authorizedMajors("MD-CS").build());
    }
    private Course course(String code, String department) {
        return courses.saveAndFlush(Course.builder().courseCode(code).courseName("合成测试课程")
                .majorCode("MD-CS").department(department).credits(3.0).hours(48).build());
    }

    @Test void sharedProfessionalScopeDoesNotGrantOtherDepartmentsCourses() {
        for (String department : List.of("MD-Lead", "MD-Base", "MD-System")) {
            user(RoleEnum.DIRECTOR, department);
            assertEquals(List.of("MD-CS"), majorController.getAllMajors().getData().stream().map(Major::getMajorCode).toList());
            assertEquals("MD-CS", CourseArchiveRules.requireMajor("md-cs", majors).getMajorCode());
            assertEquals(1, new DirectorController(null, majors, null).getManagedMajors().getData().size());
        }
        user(RoleEnum.DIRECTOR, "MD-Base");
        auth.validateCourseWrite(course("MD-OWN", "MD-Base"));
        assertThrows(ForbiddenException.class, () -> auth.validateCourseWrite(course("MD-OTHER", "MD-Lead")));
        user(RoleEnum.DIRECTOR, "MD-Foreign");
        assertTrue(majorController.getAllMajors().getData().isEmpty());
        assertThrows(ForbiddenException.class, () -> CourseArchiveRules.requireMajor("MD-CS", majors));
    }

    @Test void onlyLeadWritesSharedCatalogWhileParticipantsAndAuthorizedSupervisorsReadIt() {
        var item = IndicatorDTO.builder().indicatorCode("13-1").requirementCategory("合成类别")
                .indicatorDescription("合成测试指标").build();
        user(RoleEnum.DIRECTOR, "MD-Base");
        assertThrows(ForbiddenException.class, () -> plans.importPlanIndicators("MD-CS", "MD-v1", List.of(item)));
        user(RoleEnum.DIRECTOR, "MD-Lead");
        assertEquals(1, plans.importPlanIndicators("MD-CS", "MD-v1", List.of(item)).getData().size());
        user(RoleEnum.DIRECTOR, "MD-System");
        assertEquals(List.of("MD-v1"), plans.getPlanVersions("MD-CS").getData());
        assertEquals("13-1", plans.getPlanIndicators("MD-CS", "MD-v1").getData().get(0).getIndicatorCode());
        user(RoleEnum.TEACHER, "MD-Base");
        assertEquals(12, plans.getPlanIndicators("MD-CS", RecommendedIndicatorTemplate.VERSION).getData().size());
        user(RoleEnum.SUPERVISOR, "校督导团");
        assertEquals(1, plans.getPlanIndicators("MD-CS", "MD-v1").getData().size());
        user(RoleEnum.DIRECTOR, "MD-Foreign");
        assertThrows(ForbiddenException.class, () -> plans.getPlanIndicators("MD-CS", "MD-v1"));
    }

    @Test void participatingDepartmentCanBindCatalogAndKeepHistoricalMappings() {
        user(RoleEnum.DIRECTOR, "MD-Base");
        Course course = course("MD-MATRIX", "MD-Base");
        var item = IndicatorDTO.builder().indicatorCode("1-1").requirementCategory("工程知识")
                .indicatorDescription("合成课程分解").targetGoal("目标1").supportWeight("H").build();
        assertThrows(IllegalStateException.class, () -> plans.addIndicator(course.getId(), item));
        var first = plans.saveSyllabus(SyllabusDTO.builder().courseId(course.getId()).version("MD-v1")
                .planVersion(RecommendedIndicatorTemplate.VERSION).status("DRAFT").indicators(List.of()).build()).getData();
        var mapping = plans.addIndicator(course.getId(), item).getData();
        assertEquals(first.getId(), mapping.getSyllabus().getId());
        plans.lockSyllabus(first.getId(), "ignored");
        assertThrows(IllegalStateException.class, () -> plans.updateIndicator(mapping.getId(), item));
        plans.saveSyllabus(SyllabusDTO.builder().courseId(course.getId()).version("MD-v2")
                .planVersion(RecommendedIndicatorTemplate.VERSION).status("DRAFT").indicators(List.of()).build());
        assertEquals(1, mappings.findBySyllabusId(first.getId()).size());
        assertTrue(syllabusService.getIndicatorsByCourseId(course.getId()).isEmpty());
        plans.addIndicator(course.getId(), item);
        assertEquals(2, syllabi.findByCourseId(course.getId()).size());
        assertThrows(IllegalArgumentException.class, () -> plans.addIndicator(course.getId(),
                IndicatorDTO.builder().indicatorCode("99-1").build()));
    }
}
