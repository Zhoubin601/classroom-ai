package com.classroom.ai.modules.course.service;

import com.classroom.ai.common.exception.ForbiddenException;
import com.classroom.ai.modules.auth.context.AuthContext;
import com.classroom.ai.modules.auth.entity.RoleEnum;
import com.classroom.ai.modules.auth.vo.UserVO;
import com.classroom.ai.modules.course.dto.CourseImportRowDTO;
import com.classroom.ai.modules.course.dto.ImportConfirmDTO;
import com.classroom.ai.modules.course.entity.Course;
import com.classroom.ai.modules.course.entity.CourseImportLog;
import com.classroom.ai.modules.course.entity.Major;
import com.classroom.ai.modules.course.repository.CourseImportLogRepository;
import com.classroom.ai.modules.course.repository.CourseRepository;
import com.classroom.ai.modules.course.repository.MajorRepository;
import com.classroom.ai.modules.course.service.impl.CourseImportServiceImpl;
import com.classroom.ai.modules.course.vo.ImportPreviewVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseImportServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private MajorRepository majorRepository;

    @Mock
    private CourseImportLogRepository importLogRepository;

    @InjectMocks
    private CourseImportServiceImpl courseImportService;

    @BeforeEach
    void setUp() {
        CourseImportServiceImpl.clearBatchCache();
        AuthContext.setCurrentUser(UserVO.builder()
                .id(1L)
                .username("director_test")
                .role(RoleEnum.DIRECTOR)
                .build());
    }

    @AfterEach
    void tearDown() {
        CourseImportServiceImpl.clearBatchCache();
        AuthContext.clear();
    }

    @Test
    @DisplayName("下载模板：输出 UTF-8 BOM 以及包含标准表头内容的 CSV 模板")
    void testDownloadTemplate() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        courseImportService.downloadTemplate(out);

        byte[] bytes = out.toByteArray();
        // 校验 UTF-8 BOM 字节
        assertThat(bytes.length).isGreaterThan(3);
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);

        String text = new String(bytes, StandardCharsets.UTF_8);
        assertThat(text).contains("课程编码,课程名称,教研室,专业编码,学分,总学时,理论学时,实验学时,课程性质,先修课程编码,课程简介");
        assertThat(text).contains("CS3001,软件项目管理");
    }

    @Test
    @DisplayName("预览导入成功：两遍扫描支持批次内先修课程互相引用且学时守恒校验通过")
    void testPreviewImport_Success_WithBatchInternalPrerequisite() throws IOException {
        when(majorRepository.findAll()).thenReturn(List.of(
                Major.builder().id(101L).majorCode("SE").majorName("软件工程").build()
        ));
        when(courseRepository.findByCourseCode(any())).thenReturn(Optional.empty());

        // 行1：基础课 CS1001；行2：进阶课 CS2001，其先修课程即为行1的 CS1001
        String csv = "课程编码,课程名称,教研室,专业编码,学分,总学时,理论学时,实验学时,课程性质,先修课程编码,课程简介\n" +
                "CS1001,程序设计基础,软件工程教研室,SE,4.0,64,48,16,专业核心课,,基础编程\n" +
                "CS2001,面向对象程序设计,软件工程教研室,SE,3.0,48,32,16,专业核心课,CS1001,面向对象与Java\n";

        MockMultipartFile file = new MockMultipartFile(
                "file", "batch.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)
        );

        ImportPreviewVO vo = courseImportService.previewImport(file);

        assertThat(vo.getTotalCount()).isEqualTo(2);
        assertThat(vo.getSuccessCount()).isEqualTo(2);
        assertThat(vo.getErrorCount()).isEqualTo(0);
        assertThat(vo.getErrors()).isEmpty();
        assertThat(vo.getValidRows()).hasSize(2);
        assertThat(vo.getBatchId()).isNotBlank();

        // 验证批次缓存已存入且记录操作人
        CourseImportServiceImpl.ImportBatchCache cache = CourseImportServiceImpl.getBatchCache(vo.getBatchId());
        assertThat(cache).isNotNull();
        assertThat(cache.operator).isEqualTo("director_test");
        assertThat(cache.hasErrors).isFalse();
    }

    @Test
    @DisplayName("预览导入校验失败：缺项、学时矛盾、未知专业、批内重复、库内重复、先修不存在")
    void testPreviewImport_VariousValidationFailures() throws IOException {
        when(majorRepository.findAll()).thenReturn(List.of(
                Major.builder().id(101L).majorCode("SE").majorName("软件工程").build()
        ));
        // 模拟库中已存在 CS9999，其余课程不存在
        when(courseRepository.findByCourseCode(any())).thenAnswer(inv -> {
            String code = inv.getArgument(0);
            if ("CS9999".equals(code)) {
                return Optional.of(Course.builder().courseCode("CS9999").build());
            }
            return Optional.empty();
        });

        String csv = "课程编码,课程名称,教研室,专业编码,学分,总学时,理论学时,实验学时,课程性质,先修课程编码,课程简介\n" +
                ",空编码课,软件工程教研室,SE,3.0,48,36,12,必修,,缺编码\n" +
                "CS101,学时矛盾课,软件工程教研室,SE,3.0,48,30,10,必修,,理论30+实验10!=48\n" +
                "CS102,未知专业课,软件工程教研室,UNKNOWN_MAJOR,3.0,48,36,12,必修,,未知专业\n" +
                "CS9999,库内重复课,软件工程教研室,SE,3.0,48,36,12,必修,,已存在\n" +
                "CS103,批内重复课A,软件工程教研室,SE,3.0,48,36,12,必修,,首发\n" +
                "CS103,批内重复课B,软件工程教研室,SE,3.0,48,36,12,必修,,重发\n" +
                "CS104,孤立先修课,软件工程教研室,SE,3.0,48,36,12,必修,NON_EXIST_COURSE,找不到先修\n";

        MockMultipartFile file = new MockMultipartFile(
                "file", "bad.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)
        );

        ImportPreviewVO vo = courseImportService.previewImport(file);

        assertThat(vo.getErrorCount()).isGreaterThanOrEqualTo(6);
        List<String> reasons = vo.getErrors().stream().map(e -> e.getField() + ":" + e.getReason()).toList();

        assertThat(reasons).anyMatch(r -> r.contains("课程编码不能为空"));
        assertThat(reasons).anyMatch(r -> r.contains("理论学时(30) + 实验学时(10) 必须等于总学时(48)"));
        assertThat(reasons).anyMatch(r -> r.contains("未知的专业编码: UNKNOWN_MAJOR"));
        assertThat(reasons).anyMatch(r -> r.contains("数据库中已存在相同课程编码: CS9999"));
        assertThat(reasons).anyMatch(r -> r.contains("批次内重复课程编码: CS103"));
        assertThat(reasons).anyMatch(r -> r.contains("引用的先修课程编码 [NON_EXIST_COURSE] 在数据库及当前导入批次中均不存在"));
    }

    @Test
    @DisplayName("文件限制拦截：文件为空、超 5MB、超 1000 行均抛出异常")
    void testFileLimits() {
        // 1. 空文件
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]);
        assertThatThrownBy(() -> courseImportService.previewImport(emptyFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");

        // 2. 超 5MB
        MockMultipartFile largeFile = new MockMultipartFile("file", "large.csv", "text/csv", new byte[5 * 1024 * 1024 + 1]);
        assertThatThrownBy(() -> courseImportService.previewImport(largeFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5MB");

        // 3. 超 1000 行
        StringBuilder sb = new StringBuilder();
        sb.append("课程编码,课程名称,教研室,专业编码,学分,总学时,理论学时,实验学时,课程性质,先修课程编码,课程简介\n");
        for (int i = 1; i <= 1001; i++) {
            sb.append("CS").append(i).append(",课").append(i).append(",教研室,SE,3.0,48,36,12,必修,,\n");
        }
        MockMultipartFile over1000File = new MockMultipartFile("file", "over1000.csv", "text/csv", sb.toString().getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> courseImportService.previewImport(over1000File))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1000 行");
    }

    @Test
    @DisplayName("确认导入成功：原子整批保存并记录导入日志")
    void testConfirmImport_Success() {
        when(majorRepository.findByMajorCode("SE")).thenReturn(Optional.of(
                Major.builder().id(101L).majorCode("SE").build()
        ));
        when(courseRepository.findByCourseCode(any())).thenReturn(Optional.empty());

        // 预设批次缓存
        String batchId = UUID.randomUUID().toString();
        CourseImportServiceImpl.ImportBatchCache cache = new CourseImportServiceImpl.ImportBatchCache();
        cache.batchId = batchId;
        cache.operator = "director_test";
        cache.fileName = "valid_batch.csv";
        cache.createTime = System.currentTimeMillis();
        cache.hasErrors = false;
        cache.validRows = List.of(
                CourseImportRowDTO.builder()
                        .courseCode("CS8001")
                        .courseName("分布式系统")
                        .department("软件工程教研室")
                        .majorCode("SE")
                        .credits(3.0)
                        .hours(48)
                        .theoryHours(36)
                        .practiceHours(12)
                        .courseType("专业选修课")
                        .build()
        );
        CourseImportServiceImpl.putBatchCache(batchId, cache);

        Map<String, Object> result = courseImportService.confirmImport(new ImportConfirmDTO(batchId));

        assertThat(result.get("importedCount")).isEqualTo(1);
        assertThat(result.get("batchId")).isEqualTo(batchId);

        // 验证整批入库
        ArgumentCaptor<List<Course>> listCaptor = ArgumentCaptor.forClass(List.class);
        verify(courseRepository, times(1)).saveAll(listCaptor.capture());
        assertThat(listCaptor.getValue()).hasSize(1);
        assertThat(listCaptor.getValue().get(0).getCourseCode()).isEqualTo("CS8001");
        assertThat(listCaptor.getValue().get(0).getMajorId()).isEqualTo(101L);

        // 验证记录了日志
        verify(importLogRepository, times(1)).save(any(CourseImportLog.class));

        // 验证一次性消费：缓存已被移除
        assertThat(CourseImportServiceImpl.getBatchCache(batchId)).isNull();
    }

    @Test
    @DisplayName("确认导入防重消费：重复确认已被消费的批次抛出异常")
    void testConfirmImport_DuplicateConsumption() {
        assertThatThrownBy(() -> courseImportService.confirmImport(new ImportConfirmDTO("non-exist-batch")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不存在或已被消费确认");
    }

    @Test
    @DisplayName("确认导入越权拦截：非批次创建者确认时抛出 403 Forbidden")
    void testConfirmImport_ForbiddenOperator() {
        String batchId = UUID.randomUUID().toString();
        CourseImportServiceImpl.ImportBatchCache cache = new CourseImportServiceImpl.ImportBatchCache();
        cache.batchId = batchId;
        cache.operator = "other_user"; // 创建者为 other_user
        cache.fileName = "valid.csv";
        cache.createTime = System.currentTimeMillis();
        cache.hasErrors = false;
        cache.validRows = List.of();
        CourseImportServiceImpl.putBatchCache(batchId, cache);

        // 当前登录人为 director_test
        assertThatThrownBy(() -> courseImportService.confirmImport(new ImportConfirmDTO(batchId)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("越权拦截");
    }

    @Test
    @DisplayName("确认导入过期拦截：批次超过 30 分钟时拒绝入库")
    void testConfirmImport_ExpiredBatch() {
        String batchId = UUID.randomUUID().toString();
        CourseImportServiceImpl.ImportBatchCache cache = new CourseImportServiceImpl.ImportBatchCache();
        cache.batchId = batchId;
        cache.operator = "director_test";
        cache.fileName = "valid.csv";
        cache.createTime = System.currentTimeMillis() - (31 * 60 * 1000L); // 31分钟前
        cache.hasErrors = false;
        cache.validRows = List.of();
        CourseImportServiceImpl.putBatchCache(batchId, cache);

        assertThatThrownBy(() -> courseImportService.confirmImport(new ImportConfirmDTO(batchId)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("30分钟有效期限");
    }

    @Test
    @DisplayName("确认导入整批回滚保护：存在错误的批次禁止确认")
    void testConfirmImport_RejectBatchWithErrors() {
        String batchId = UUID.randomUUID().toString();
        CourseImportServiceImpl.ImportBatchCache cache = new CourseImportServiceImpl.ImportBatchCache();
        cache.batchId = batchId;
        cache.operator = "director_test";
        cache.fileName = "bad.csv";
        cache.createTime = System.currentTimeMillis();
        cache.hasErrors = true; // 包含错误行
        cache.validRows = List.of();
        CourseImportServiceImpl.putBatchCache(batchId, cache);

        assertThatThrownBy(() -> courseImportService.confirmImport(new ImportConfirmDTO(batchId)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("整批回滚保护");
    }
}
