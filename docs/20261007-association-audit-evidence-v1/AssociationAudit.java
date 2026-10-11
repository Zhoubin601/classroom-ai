import com.alibaba.fastjson2.JSON;
import com.classroom.ai.modules.auth.context.AuthContext;
import com.classroom.ai.modules.auth.entity.RoleEnum;
import com.classroom.ai.modules.auth.repository.UserAccountRepository;
import com.classroom.ai.modules.auth.security.*;
import com.classroom.ai.modules.auth.vo.UserVO;
import com.classroom.ai.modules.attendance.dto.StartAttendanceDTO;
import com.classroom.ai.modules.attendance.entity.AttendanceSession;
import com.classroom.ai.modules.attendance.repository.AttendanceSessionRepository;
import com.classroom.ai.modules.attendance.service.impl.AttendanceServiceImpl;
import com.classroom.ai.modules.course.controller.SyllabusController;
import com.classroom.ai.modules.course.dto.*;
import com.classroom.ai.modules.course.entity.*;
import com.classroom.ai.modules.course.repository.*;
import com.classroom.ai.modules.course.service.*;
import com.classroom.ai.modules.course.service.impl.*;
import com.classroom.ai.repository.StudentRepository;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.mockito.Mockito.*;

/** Diagnostic probes of the unchanged business classes. All persistence is mocked. */
public class AssociationAudit {
    static final List<Map<String,Object>> results = new ArrayList<>();
    static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static void user(RoleEnum role, String department) {
        AuthContext.setCurrentUser(UserVO.builder().id(900L).username("association-audit")
            .realName("诊断账号").role(role).department(department).teacherCode("T1").authorizedMajors("SE").build());
    }
    static void probe(String id, String title, Callable<String> action) {
        try {
            user(RoleEnum.DIRECTOR,"A");
            results.add(Map.of("id",id,"title",title,"status","confirmed","observed",action.call()));
        } catch (Throwable error) {
            results.add(Map.of("id",id,"title",title,"status","inconclusive","error",error.getClass().getSimpleName()+": "+error.getMessage()));
        } finally { AuthContext.clear(); SecurityContextHolder.clearContext(); }
    }
    static class Fixture {
        CourseRepository courses = mock(CourseRepository.class);
        CourseOfferingRepository offerings = mock(CourseOfferingRepository.class);
        CourseOfferingTeacherRepository relations = mock(CourseOfferingTeacherRepository.class);
        CourseSyllabusRepository syllabi = mock(CourseSyllabusRepository.class);
        GraduationIndicatorRepository mappings = mock(GraduationIndicatorRepository.class);
        TrainingIndicatorRepository catalog = mock(TrainingIndicatorRepository.class);
        MajorRepository majors = mock(MajorRepository.class);
        Course course = Course.builder().id(1L).courseCode("AUDIT-C1").courseName("诊断课程")
            .department("A").majorCode("SE").majorId(1L).credits(3.0).hours(48).build();
        CourseSyllabus current = CourseSyllabus.builder().id(20L).course(course).version("v2")
            .planVersion(RecommendedIndicatorTemplate.VERSION).status("DRAFT").build();
        CourseAuthorizationService auth = new CourseAuthorizationService(courses,offerings,relations);
        SyllabusServiceImpl service = new SyllabusServiceImpl(syllabi,mappings,courses);
        Fixture() {
            when(courses.findById(1L)).thenReturn(Optional.of(course));
            when(courses.findForUpdate(1L)).thenReturn(Optional.of(course));
            when(courses.findByCourseCode("AUDIT-C1")).thenReturn(Optional.of(course));
            when(courses.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(syllabi.findById(20L)).thenReturn(Optional.of(current));
            when(syllabi.findFirstByCourseIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(current));
            when(syllabi.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mappings.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(majors.findByMajorCode("SE")).thenReturn(Optional.of(Major.builder().id(1L).majorCode("SE").department("A").build()));
            ReflectionTestUtils.setField(service,"trainingIndicatorRepository",catalog);
        }
        SyllabusController controller() { return new SyllabusController(service,auth,mappings,catalog,majors,syllabi,courses); }
        CourseOffering offering(long id) {
            return CourseOffering.builder().id(id).course(course).teacherCode("T1").teacherName("诊断教师")
                .majorCode("SE").majorId(1L).academicTerm("2026秋").className("诊断班").studentCount(30).build();
        }
    }
    public static void main(String[] args) throws Exception {
        probe("A01","指标编号与大项不匹配仍可保存", () -> {
            var f = new Fixture();
            var actual = f.service.addIndicator(1L,IndicatorDTO.builder().indicatorCode("2-1")
                .requirementCategory("工程知识").indicatorDescription("诊断").supportWeight("H").build());
            require(actual.getIndicatorCode().equals("2-1") && actual.getRequirementCategory().equals("工程知识"),"未观察到错误配对");
            return "2-1（问题分析）被写为工程知识，业务服务未拒绝";
        });
        probe("A02","同一大纲重复指标编号未拒绝", () -> {
            var f = new Fixture();
            var existing = GraduationIndicator.builder().id(80L).course(f.course).syllabus(f.current)
                .indicatorCode("1-1").requirementCategory("工程知识").supportWeight("M").build();
            when(f.mappings.findBySyllabusId(20L)).thenReturn(List.of(existing));
            f.service.addIndicator(1L,IndicatorDTO.builder().indicatorCode("1-1").requirementCategory("工程知识")
                .indicatorDescription("再次新增").supportWeight("H").build());
            verify(f.mappings).save(any());
            return "已有1-1时仍调用保存；实体只有普通索引，无大纲+编号唯一约束";
        });
        probe("A03","整份保存可修改历史未锁定大纲", () -> {
            var f = new Fixture();
            var old = CourseSyllabus.builder().id(10L).course(f.course).version("v1")
                .planVersion(RecommendedIndicatorTemplate.VERSION).status("DRAFT").courseGoals("历史目标").build();
            when(f.syllabi.findById(10L)).thenReturn(Optional.of(old));
            f.service.saveSyllabus(SyllabusDTO.builder().id(10L).courseId(1L).version("v1")
                .planVersion(RecommendedIndicatorTemplate.VERSION).status("DRAFT").courseGoals("改写历史目标").build());
            require(old.getCourseGoals().equals("改写历史目标"),"历史记录未被改写");
            return "当前为v2，但POST整份保存接受v1原地更新；与单条指标历史保护不一致";
        });
        probe("A04","省略目录版本导致现有绑定被重置", () -> {
            var f = new Fixture();
            f.service.saveSyllabus(SyllabusDTO.builder().id(20L).courseId(1L).status("DRAFT").courseGoals("更新目标").build());
            require(f.current.getPlanVersion().equals("v2"),"未观察到目录绑定改变");
            return "RECOMMENDED-12变为大纲版本v2，原指标目录可能无法加载";
        });
        probe("A05","已引用目录重导可删除被引用指标", () -> {
            var f = new Fixture();
            TrainingIndicator one = new TrainingIndicator(); one.setIndicatorCode("1-1");
            TrainingIndicator two = new TrainingIndicator(); two.setIndicatorCode("2-1");
            when(f.catalog.findByMajorCodeAndPlanVersionOrderByIndicatorCode("SE","audit-plan")).thenReturn(List.of(one,two));
            f.current.setPlanVersion("audit-plan");
            f.current.setStatus("LOCKED");
            when(f.mappings.findBySyllabusId(20L)).thenReturn(List.of(GraduationIndicator.builder()
                .id(81L).course(f.course).syllabus(f.current).indicatorCode("2-1").requirementCategory("问题分析").build()));
            when(f.syllabi.findByCourseId(1L)).thenReturn(List.of(f.current));
            var deleted = new AtomicBoolean();
            doAnswer(inv -> { deleted.set(((List<?>)inv.getArgument(0)).contains(two)); return null; }).when(f.catalog).deleteAll(any());
            f.controller().importPlanIndicators("SE","audit-plan",List.of(IndicatorDTO.builder()
                .indicatorCode("1-1").requirementCategory("工程知识").indicatorDescription("新版目录").build()));
            require(deleted.get(),"未删除遗漏的被引用编号");
            return "同版本重导直接deleteAll遗漏项，不检查大纲引用或锁定状态";
        });
        probe("A06","明确跨室任课可读课程但不能读其目录", () -> {
            var f = new Fixture(); user(RoleEnum.TEACHER,"B");
            when(f.offerings.findByCourseId(1L)).thenReturn(List.of(f.offering(100L)));
            f.auth.validateCourseRead(f.course);
            try { f.controller().getPlanIndicators("SE",RecommendedIndicatorTemplate.VERSION); }
            catch (com.classroom.ai.common.exception.ForbiddenException expected) {
                return "真实课程授权通过，目录按教师部门B判定，返回ForbiddenException";
            }
            throw new AssertionError("目录读取没有被拒绝");
        });
        probe("A07","已有教学班可更换所属课程", () -> {
            var f = new Fixture(); var old = f.offering(100L);
            var other = Course.builder().id(2L).courseCode("AUDIT-C2").courseName("另一课程").department("A").majorCode("SE").majorId(1L).build();
            when(f.offerings.findForUpdate(100L)).thenReturn(Optional.of(old));
            when(f.courses.findById(2L)).thenReturn(Optional.of(other));
            when(f.offerings.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
            var teachers = mock(TeacherRepository.class);
            when(teachers.findById(1L)).thenReturn(Optional.of(Teacher.builder().id(1L).teacherCode("T1").teacherName("诊断教师").build()));
            var conflicts = mock(ScheduleConflictService.class);
            when(conflicts.hydrate(any())).thenAnswer(inv -> inv.getArgument(0));
            var service = new CourseOfferingManagementService(f.offerings,f.courses,teachers,f.relations,
                mock(OfferingStudentEnrollmentRepository.class),mock(StudentRepository.class),mock(CourseScheduleRepository.class),
                mock(AcademicTermLockService.class),conflicts,f.auth);
            var dto = new CourseOfferingDTO(); dto.setCourseId(2L); dto.setAcademicTerm("2026秋");dto.setClassName("诊断班");
            dto.setPrimaryTeacherId(1L);dto.setCollaboratingTeacherIds(List.of());dto.setStudentNumbers(List.of());
            service.save(100L,dto);
            require(old.getCourse().getId().equals(2L),"教学班课程未改变");
            return "教学班100从课程1改成2；绑定该班次ID的考勤/评价不会随业务语义独立迁移";
        });
        probe("A08","课程更换专业不处理已有班次专业缓存", () -> {
            var f = new Fixture(); var off = f.offering(100L);
            when(f.offerings.findByCourseId(1L)).thenReturn(List.of(off));
            when(f.majors.findByMajorCode("CS")).thenReturn(Optional.of(Major.builder().id(2L).majorCode("CS").department("A").build()));
            var service = new CourseServiceImpl(f.courses,f.offerings,mock(StudentRepository.class),mock(OfferingStudentEnrollmentRepository.class),f.majors);
            var dto = CourseDTO.builder().id(1L).courseCode("AUDIT-C1").courseName("诊断课程").department("A")
                .majorCode("CS").credits(3.0).hours(48).courseType("专业核心课").build();
            service.saveCourse(dto);
            require(f.course.getMajorCode().equals("CS") && off.getMajorCode().equals("SE"),"未观察到专业分叉");
            return "同室兼管SE/CS时允许修改，课程成为CS而已有班次缓存仍SE，大纲也依赖课程当前专业";
        });
        probe("A09","考勤服务接受外部班次和客户端操作人", () -> {
            var f = new Fixture(); user(RoleEnum.TEACHER,"B");
            AuthContext.getCurrentUser().setTeacherCode("T-OUTSIDER");
            var off = f.offering(100L);
            boolean forbidden = false;
            try { f.auth.validateOfferingRead(off); } catch (com.classroom.ai.common.exception.ForbiddenException expected) { forbidden = true; }
            require(forbidden,"基准课程权限没有拒绝外部班次");
            when(f.offerings.findById(100L)).thenReturn(Optional.of(off));
            var sessions = mock(AttendanceSessionRepository.class);
            when(sessions.save(any())).thenAnswer(inv -> inv.getArgument(0));
            var service = new AttendanceServiceImpl(sessions,f.offerings);
            var actual = service.startSession(StartAttendanceDTO.builder().offeringId(100L)
                .operatorName("冒用操作人").operatorRole("DIRECTOR").operatorTitle("冒用头衔").build());
            require(actual.getOffering().getId().equals(100L) && actual.getOperatorRole().equals("DIRECTOR"),"未观察到越域考勤/身份冒用");
            when(sessions.findFirstByStatusOrderByCreatedAtDesc("ACTIVE")).thenReturn(Optional.of(actual));
            require(service.getCurrentActiveSession() == actual,"全局会话未返回外部班次");
            return "当前无班次关联权限的教师可启动其考勤，并写入请求指定主任身份；current全局读取亦未按范围过滤";
        });
        probe("A10","账号已不存在仍接受原JWT身份", () -> {
            var provider = mock(JwtTokenProvider.class); var accounts = mock(UserAccountRepository.class);
            when(provider.validateToken("audit-synthetic-token")).thenReturn(true);
            when(provider.parseUserFromToken("audit-synthetic-token")).thenReturn(UserVO.builder().id(900L)
                .username("deleted-audit-account").role(RoleEnum.DIRECTOR).department("A").build());
            var request = new MockHttpServletRequest(); request.addHeader("Authorization","Bearer audit-synthetic-token");
            var trusted = new AtomicBoolean();
            new JwtAuthenticationFilter(provider,accounts).doFilter(request,new MockHttpServletResponse(),(req,res) -> {
                trusted.set(AuthContext.getCurrentUser() != null && SecurityContextHolder.getContext().getAuthentication() != null);
            });
            require(trusted.get(),"失效账号令牌未获信任");
            return "按ID和用户名都查不到账号时，过滤器继续使用JWT旧角色和教研室";
        });
        probe("A11","先修关系接受自身及互相依赖", () -> {
            var f = new Fixture();
            var service = new CourseServiceImpl(f.courses,f.offerings,mock(StudentRepository.class),mock(OfferingStudentEnrollmentRepository.class),f.majors);
            var dto = CourseDTO.builder().id(1L).courseCode("AUDIT-C1").courseName("诊断课程").department("A")
                .majorCode("SE").credits(3.0).hours(48).courseType("专业核心课").prerequisites("AUDIT-C1").build();
            service.saveCourse(dto);
            require("AUDIT-C1".equals(f.course.getPrerequisites()),"未保存自身先修");
            var other = Course.builder().id(2L).courseCode("AUDIT-C2").prerequisites("AUDIT-C1").build();
            when(f.courses.findByCourseCode("AUDIT-C2")).thenReturn(Optional.of(other));
            dto.setPrerequisites("AUDIT-C2"); service.saveCourse(dto);
            require("AUDIT-C2".equals(f.course.getPrerequisites()),"未保存互相先修");
            return "真实保存接受C1自身先修，也接受C1→C2→C1；只判断引用存在，没有自身/循环检查";
        });
        probe("A12","已有无先修标记被当作课程引用拒绝", () -> {
            var f = new Fixture();
            try { CourseArchiveRules.validatePrerequisites("无",Set.of(),f.courses); }
            catch (IllegalArgumentException expected) {
                require(expected.getMessage().contains("先修课程"),"异常原因不同");
                return "无被当作课程名称/编码查找，正常档案更新被先修存在性规则拒绝";
            }
            throw new AssertionError("无先修标记未被拒绝");
        });
        Files.writeString(Path.of(args[0]),JSON.toJSONString(Map.of("baseline","68255ad","persistence","mocked only; no live data writes","findings",results)));
        System.out.println(JSON.toJSONString(results));
        if (results.stream().anyMatch(r -> !r.get("status").equals("confirmed"))) System.exit(1);
    }
}
