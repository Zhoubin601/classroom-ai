import com.classroom.ai.common.exception.ForbiddenException;
import com.classroom.ai.modules.auth.context.AuthContext;
import com.classroom.ai.modules.auth.entity.RoleEnum;
import com.classroom.ai.modules.auth.vo.UserVO;
import com.classroom.ai.modules.course.controller.CourseContentController;
import com.classroom.ai.modules.course.dto.*;
import com.classroom.ai.modules.course.entity.*;
import com.classroom.ai.modules.course.repository.*;
import com.classroom.ai.modules.course.service.*;
import com.classroom.ai.modules.course.service.impl.*;
import com.classroom.ai.modules.course.vo.ImportPreviewVO;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Isolated review probes: real business code, mocked persistence; no database writes. */
public class ReviewProbes {
    static int failures;
    interface Checked { void run() throws Exception; }
    static void check(boolean ok, String detail) { if (!ok) throw new AssertionError(detail); }
    static void probe(String name, Checked test) {
        try { test.run(); System.out.println("PASS " + name); }
        catch (AssertionError e) { failures++; System.out.println("FAIL " + name + " :: " + e.getMessage()); }
        catch (Exception e) { failures++; System.out.println("ERROR " + name + " :: " + e); }
        finally { AuthContext.clear(); CourseImportServiceImpl.clearBatchCache(); }
    }
    static void user(RoleEnum role, String name, String department) {
        AuthContext.setCurrentUser(UserVO.builder().id(900L).username(name).realName(name)
            .teacherCode("REVIEW-T").role(role).department(department).authorizedMajors("SE").build());
    }
    static class Imports {
        CourseRepository courses = mock(CourseRepository.class);
        MajorRepository majors = mock(MajorRepository.class);
        CourseImportLogRepository logs = mock(CourseImportLogRepository.class);
        CourseImportServiceImpl service = new CourseImportServiceImpl(courses, majors, logs);
        Imports() {
            Major major = Major.builder().id(1L).majorCode("SE").build();
            when(majors.findAll()).thenReturn(List.of(major));
            when(majors.findByMajorCode("SE")).thenReturn(Optional.of(major));
            user(RoleEnum.DIRECTOR, "review_director", "Department A");
        }
        ImportPreviewVO preview(String row) throws Exception {
            String header = "courseCode,courseName,department,majorCode,credits,hours,theoryHours,practiceHours,courseType,prerequisites,description\n";
            return service.previewImport(new MockMultipartFile("file", "synthetic.csv", "text/csv", (header + row).getBytes(StandardCharsets.UTF_8)));
        }
    }
    static class Content {
        CourseRepository courses = mock(CourseRepository.class);
        CourseContentRevisionRepository revisions = mock(CourseContentRevisionRepository.class);
        CourseOfferingRepository offerings = mock(CourseOfferingRepository.class);
        CourseOfferingTeacherRepository teachers = mock(CourseOfferingTeacherRepository.class);
        CourseAuthorizationService auth = new CourseAuthorizationService(courses, offerings, teachers);
        CourseContentController controller = new CourseContentController(courses, revisions, auth);
        Course course = Course.builder().id(1L).courseCode("R1").courseName("Synthetic")
            .majorCode("CS").department("Department B").teacherName("review_teacher").build();
        Content() {
            user(RoleEnum.TEACHER, "review_teacher", "Department B");
            when(courses.findById(1L)).thenReturn(Optional.of(course));
            when(revisions.save(any())).thenAnswer(i -> i.getArgument(0));
        }
        ContentRevisionDTO dto(Integer lock) {
            return ContentRevisionDTO.builder().description("STALE text").assessmentMethod("exam")
                .objectives("learn").lockVersion(lock).build();
        }
    }
    public static void main(String[] args) {
        probe("01 supervisor import must be forbidden", () -> {
            Imports f = new Imports(); user(RoleEnum.SUPERVISOR, "review_supervisor", "Department A");
            try {
                var p = f.preview("R1,Synthetic,Department B,SE,3,48,36,12,core,,text\n");
                f.service.confirmImport(new ImportConfirmDTO(p.getBatchId()));
            } catch (ForbiddenException expected) { return; }
            verify(f.courses).saveAll(any());
            throw new AssertionError("SUPERVISOR reached saveAll successfully");
        });
        probe("02 director cross-department import must be forbidden", () -> {
            Imports f = new Imports();
            try {
                var p = f.preview("R1,Synthetic,Department B,SE,3,48,36,12,core,,text\n");
                f.service.confirmImport(new ImportConfirmDTO(p.getBatchId()));
            } catch (ForbiddenException expected) { return; }
            throw new AssertionError("Department A director imported Department B course");
        });
        probe("03 partial hours must conserve total", () -> {
            Imports f = new Imports(); var p = f.preview("R1,Synthetic,Department A,SE,3,48,,12,core,,text\n");
            check(p.getErrorCount() > 0 || p.getValidRows().get(0).getTheoryHours() + p.getValidRows().get(0).getPracticeHours() == 48,
                "preview accepted total=48, theory=48, practice=12");
        });
        probe("04 preview total must count rows not field errors", () -> {
            Imports f = new Imports(); var p = f.preview("R1,Synthetic,Department A,SE,-1,-2,0,0,core,,text\n");
            check(p.getTotalCount() == 1, "one input row reported total=" + p.getTotalCount() + ", errors=" + p.getErrorCount());
        });
        probe("05 confirm must revalidate deleted prerequisite and major", () -> {
            Imports f = new Imports(); when(f.courses.findByCourseCode("BASE")).thenReturn(Optional.of(Course.builder().courseCode("BASE").build()));
            var p = f.preview("R1,Synthetic,Department A,SE,3,48,36,12,core,BASE,text\n");
            check(p.getErrorCount() == 0, "fixture must preview cleanly");
            when(f.courses.findByCourseCode("BASE")).thenReturn(Optional.empty());
            when(f.majors.findByMajorCode("SE")).thenReturn(Optional.empty());
            try { f.service.confirmImport(new ImportConfirmDTO(p.getBatchId())); }
            catch (IllegalArgumentException | IllegalStateException expected) { return; }
            throw new AssertionError("confirmation accepted deleted references and reached saveAll");
        });
        probe("06 unauthorized confirmation must preserve owner batch", () -> {
            Imports f = new Imports(); var p = f.preview("R1,Synthetic,Department A,SE,3,48,36,12,core,,text\n");
            user(RoleEnum.DIRECTOR, "another_director", "Department A");
            try { f.service.confirmImport(new ImportConfirmDTO(p.getBatchId())); } catch (ForbiddenException expected) { }
            check(CourseImportServiceImpl.getBatchCache(p.getBatchId()) != null, "403 consumes the original owner's batch");
        });
        probe("07 published read must reject unauthorized major", () -> {
            Content f = new Content(); user(RoleEnum.SUPERVISOR, "review_supervisor", "Department A");
            when(f.revisions.findFirstByCourseIdAndStatusOrderByPublishVersionDesc(1L, "PUBLISHED"))
                .thenReturn(Optional.of(CourseContentRevision.builder().courseId(1L).description("restricted").publishVersion(1).status("PUBLISHED").build()));
            try { f.controller.getPublishedContent(1L); } catch (ForbiddenException expected) { return; }
            throw new AssertionError("SE-only supervisor read CS course published content");
        });
        probe("08 stale publish after another publisher must conflict", () -> {
            Content f = new Content();
            when(f.revisions.findFirstByCourseIdAndStatusOrderByPublishVersionDesc(1L, "PUBLISHED"))
                .thenReturn(Optional.of(CourseContentRevision.builder().courseId(1L).publishVersion(1).status("PUBLISHED").description("newer text").build()));
            try { f.controller.publishContent(1L, f.dto(0)); } catch (IllegalStateException expected) { return; }
            throw new AssertionError("no remaining draft: stale lock=0 published v2 and overwrote Course");
        });
        probe("09 lock reset across drafts must not accept old page", () -> {
            Content f = new Content();
            when(f.revisions.findFirstByCourseIdAndStatusOrderByVersionDesc(1L, "DRAFT"))
                .thenReturn(Optional.of(CourseContentRevision.builder().id(20L).courseId(1L).publishVersion(1).lockVersion(0).status("DRAFT").build()));
            try { f.controller.saveDraft(1L, f.dto(0)); } catch (IllegalStateException expected) { return; }
            throw new AssertionError("stale page from previous draft accepted by new draft with lock=0");
        });
        probe("10 missing lock must not bypass conflict checks", () -> {
            Content f = new Content();
            when(f.revisions.findFirstByCourseIdAndStatusOrderByVersionDesc(1L, "DRAFT"))
                .thenReturn(Optional.of(CourseContentRevision.builder().id(10L).courseId(1L).lockVersion(5).status("DRAFT").build()));
            try { f.controller.saveDraft(1L, f.dto(null)); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
            throw new AssertionError("lockVersion/version omitted: existing draft overwritten");
        });
        probe("11 teacher identity must use stable identifier", () -> {
            Content f = new Content(); f.course.setTeacherName("review_teacher");
            user(RoleEnum.TEACHER, "teacher", "Department A");
            try { f.auth.validateTeacherCoursePublish(1L); } catch (ForbiddenException expected) { return; }
            throw new AssertionError("unrelated teacher accepted because realName is substring of teacherName");
        });
        probe("12 manual creation requires valid major and prerequisites", () -> {
            Imports f = new Imports();
            CourseServiceImpl service = new CourseServiceImpl(f.courses, mock(CourseOfferingRepository.class),
                mock(com.classroom.ai.repository.StudentRepository.class), mock(OfferingStudentEnrollmentRepository.class), f.majors);
            CourseDTO dto = CourseDTO.builder().courseCode("R1").courseName("Synthetic").department("Department A")
                .credits(3.0).hours(48).courseType("core").prerequisites("NONEXISTENT").build();
            try { service.saveCourse(dto); } catch (IllegalArgumentException expected) { return; }
            verify(f.courses).save(any());
            throw new AssertionError("missing major and nonexistent prerequisite accepted");
        });
        System.out.println("SUMMARY: 12 acceptance probes, " + failures + " unmet expectations. Persistence mocked; no live DB/browser coverage.");
        if (failures != 0) System.exit(1);
    }
}
