package com.classroom.ai.modules.course.controller;

import com.classroom.ai.common.ApiResponse;
import com.classroom.ai.common.exception.ForbiddenException;
import com.classroom.ai.common.exception.UnauthorizedException;
import com.classroom.ai.modules.auth.context.AuthContext;
import com.classroom.ai.modules.auth.entity.RoleEnum;
import com.classroom.ai.modules.auth.vo.UserVO;
import com.classroom.ai.modules.course.dto.CourseImportRowDTO;
import com.classroom.ai.modules.course.dto.ImportConfirmDTO;
import com.classroom.ai.modules.course.entity.Course;
import com.classroom.ai.modules.course.entity.Major;
import com.classroom.ai.modules.course.repository.CourseRepository;
import com.classroom.ai.modules.course.repository.MajorRepository;
import com.classroom.ai.modules.course.vo.ImportPreviewVO;
import com.classroom.ai.modules.course.vo.ImportRowError;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/courses/import")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin
public class CourseImportController {

    private final CourseRepository courseRepository;
    private final MajorRepository majorRepository;

    // 内存中维护与操作人绑定的导入预览批次，30 分钟过期
    private static final long BATCH_EXPIRE_MS = 30 * 60 * 1000L;
    private static final Map<String, ImportBatchCache> BATCH_CACHE = new ConcurrentHashMap<>();

    private static class ImportBatchCache {
        String batchId;
        String operator;
        long createTime;
        List<CourseImportRowDTO> validRows;
        boolean hasErrors;
    }

    /**
     * 下载标准课程导入 CSV 模板 (带 UTF-8 BOM，兼容各类 Excel 及文本编辑器)
     */
    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) throws Exception {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"course_import_template.csv\"");

        // 写入 UTF-8 BOM 字节
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        response.getOutputStream().write(bom);

        String content = "课程编码,课程名称,教研室,专业编码,学分,总学时,理论学时,实验学时,课程性质,先修课程编码,课程简介\n" +
                "CS3001,软件项目管理,软件工程教研室,SE,3.0,48,36,12,专业核心课,CS1001;CS2001,系统讲授现代软件工程项目管理实践。\n" +
                "CS3002,数据库系统概论,计算机科学教研室,CS,4.0,64,48,16,专业核心课,CS1002,全面介绍关系型数据库与SQL核心原理。\n";

        response.getOutputStream().write(content.getBytes(StandardCharsets.UTF_8));
        response.getOutputStream().flush();
    }

    /**
     * 课程 CSV 上传校验与预览 (US-01 两阶段导入之预览，不写入数据库)
     */
    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ImportPreviewVO> previewImport(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            return ApiResponse.error(400, "上传文件不能为空");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            return ApiResponse.error(400, "文件大小超出 5MB 限制");
        }

        List<CourseImportRowDTO> validRows = new ArrayList<>();
        List<ImportRowError> errors = new ArrayList<>();
        Set<String> seenCodesInBatch = new HashSet<>();

        // 预加载所有有效专业字典缓存
        Set<String> validMajorCodes = new HashSet<>();
        for (Major m : majorRepository.findAll()) {
            validMajorCodes.add(m.getMajorCode().toUpperCase());
        }

        int rowNumber = 1;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean isHeader = true;
            while ((line = reader.readLine()) != null) {
                // 去除可能存在的 UTF-8 BOM
                if (rowNumber == 1 && line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }
                if (line.trim().isEmpty()) {
                    rowNumber++;
                    continue;
                }

                if (isHeader) {
                    isHeader = false;
                    rowNumber++;
                    continue;
                }

                if (rowNumber > 1001) {
                    errors.add(new ImportRowError(rowNumber, "文件行数", "单批次导入不得超过 1000 行"));
                    break;
                }

                String[] cols = line.split(",", -1);
                validateAndParseRow(cols, rowNumber, validMajorCodes, seenCodesInBatch, validRows, errors);
                rowNumber++;
            }
        }

        int totalCount = validRows.size() + errors.size();
        String batchId = UUID.randomUUID().toString();

        String operator = AuthContext.isAuthenticated() ? AuthContext.getCurrentUser().getUsername() : "ANONYMOUS";

        ImportBatchCache cache = new ImportBatchCache();
        cache.batchId = batchId;
        cache.operator = operator;
        cache.createTime = System.currentTimeMillis();
        cache.validRows = validRows;
        cache.hasErrors = !errors.isEmpty();

        BATCH_CACHE.put(batchId, cache);

        ImportPreviewVO vo = ImportPreviewVO.builder()
                .batchId(batchId)
                .totalCount(totalCount)
                .successCount(validRows.size())
                .errorCount(errors.size())
                .errors(errors)
                .validRows(validRows)
                .build();

        return ApiResponse.success("导入解析完成", vo);
    }

    private void validateAndParseRow(String[] cols, int rowNumber, Set<String> validMajorCodes,
                                     Set<String> seenCodesInBatch, List<CourseImportRowDTO> validRows,
                                     List<ImportRowError> errors) {
        if (cols.length < 7) {
            errors.add(new ImportRowError(rowNumber, "格式错误", "列数不足，至少需提供前7项必填列"));
            return;
        }

        String courseCode = cols[0].trim();
        String courseName = cols[1].trim();
        String department = cols[2].trim();
        String majorCode = cols[3].trim();
        String creditsStr = cols[4].trim();
        String hoursStr = cols[5].trim();
        String theoryHoursStr = cols.length > 6 ? cols[6].trim() : "";
        String practiceHoursStr = cols.length > 7 ? cols[7].trim() : "";
        String courseType = cols.length > 8 ? cols[8].trim() : "";
        String prerequisites = cols.length > 9 ? cols[9].trim() : "";
        String description = cols.length > 10 ? cols[10].trim() : "";

        // 1. 必填校验
        if (courseCode.isEmpty()) errors.add(new ImportRowError(rowNumber, "课程编码", "课程编码不能为空"));
        if (courseName.isEmpty()) errors.add(new ImportRowError(rowNumber, "课程名称", "课程名称不能为空"));
        if (department.isEmpty()) errors.add(new ImportRowError(rowNumber, "教研室", "教研室不能为空"));
        if (majorCode.isEmpty()) errors.add(new ImportRowError(rowNumber, "专业编码", "专业编码不能为空"));
        if (courseType.isEmpty()) errors.add(new ImportRowError(rowNumber, "课程性质", "课程性质不能为空"));

        // 2. 编码唯一性校验 (批内重复 & 数据库重复)
        if (!courseCode.isEmpty()) {
            if (seenCodesInBatch.contains(courseCode)) {
                errors.add(new ImportRowError(rowNumber, "课程编码", "批次内重复课程编码: " + courseCode));
            } else {
                seenCodesInBatch.add(courseCode);
            }

            if (courseRepository.findByCourseCode(courseCode).isPresent()) {
                errors.add(new ImportRowError(rowNumber, "课程编码", "数据库中已存在相同课程编码: " + courseCode + " (首版仅支持新增)"));
            }
        }

        // 3. 专业引用有效性校验
        if (!majorCode.isEmpty() && !validMajorCodes.contains(majorCode.toUpperCase())) {
            errors.add(new ImportRowError(rowNumber, "专业编码", "未知的专业编码: " + majorCode + "，请在专业字典中维护后引用"));
        }

        // 4. 学分与学时数值及守恒校验
        Double credits = null;
        Integer hours = null;
        Integer theory = null;
        Integer practice = null;

        try {
            credits = Double.parseDouble(creditsStr);
            if (credits <= 0) errors.add(new ImportRowError(rowNumber, "学分", "学分必须大于 0"));
        } catch (Exception e) {
            errors.add(new ImportRowError(rowNumber, "学分", "学分格式无效，必须为有效数值"));
        }

        try {
            hours = Integer.parseInt(hoursStr);
            if (hours <= 0) errors.add(new ImportRowError(rowNumber, "总学时", "总学时必须大于 0"));
        } catch (Exception e) {
            errors.add(new ImportRowError(rowNumber, "总学时", "总学时格式无效，必须为有效整数"));
        }

        if (!theoryHoursStr.isEmpty()) {
            try { theory = Integer.parseInt(theoryHoursStr); } catch (Exception e) {
                errors.add(new ImportRowError(rowNumber, "理论学时", "理论学时必须为整数"));
            }
        }
        if (!practiceHoursStr.isEmpty()) {
            try { practice = Integer.parseInt(practiceHoursStr); } catch (Exception e) {
                errors.add(new ImportRowError(rowNumber, "实验学时", "实验学时必须为整数"));
            }
        }

        if (hours != null && theory != null && practice != null) {
            if (theory + practice != hours) {
                errors.add(new ImportRowError(rowNumber, "学时关系", "理论学时(" + theory + ") + 实验学时(" + practice + ") 必须等于总学时(" + hours + ")"));
            }
        }

        // 若本行无错误，装配 DTO
        if (errors.stream().noneMatch(e -> e.getRowNumber() == rowNumber)) {
            validRows.add(CourseImportRowDTO.builder()
                    .rowNumber(rowNumber)
                    .courseCode(courseCode)
                    .courseName(courseName)
                    .department(department)
                    .majorCode(majorCode.toUpperCase())
                    .credits(credits)
                    .hours(hours)
                    .theoryHours(theory)
                    .practiceHours(practice)
                    .courseType(courseType)
                    .prerequisites(prerequisites)
                    .description(description)
                    .build());
        }
    }

    /**
     * 确认导入批次入库 (US-01 事务性写入，整批全成功才入库)
     */
    @PostMapping("/confirm")
    @Transactional
    public ApiResponse<Map<String, Object>> confirmImport(@RequestBody ImportConfirmDTO dto) {
        if (dto.getBatchId() == null) {
            return ApiResponse.error(400, "批次 ID 不能为空");
        }

        ImportBatchCache cache = BATCH_CACHE.get(dto.getBatchId());
        if (cache == null) {
            return ApiResponse.error(400, "导入批次不存在或已失效，请重新上传文件预览");
        }

        if (System.currentTimeMillis() - cache.createTime > BATCH_EXPIRE_MS) {
            BATCH_CACHE.remove(dto.getBatchId());
            return ApiResponse.error(400, "导入批次已超过30分钟有效期限，请重新上传预览");
        }

        // 整批全成功才允许写入
        if (cache.hasErrors) {
            return ApiResponse.error(400, "当前批次存在校验错误行，系统实行整批回滚保护，禁止部分入库");
        }

        // 重新核对数据库防并发插入
        List<Course> toSave = new ArrayList<>();
        for (CourseImportRowDTO r : cache.validRows) {
            if (courseRepository.findByCourseCode(r.getCourseCode()).isPresent()) {
                throw new IllegalStateException("并发冲突：课程编码 " + r.getCourseCode() + " 已被其他操作者写入");
            }

            Major major = majorRepository.findByMajorCode(r.getMajorCode()).orElse(null);

            Course c = Course.builder()
                    .courseCode(r.getCourseCode())
                    .courseName(r.getCourseName())
                    .department(r.getDepartment())
                    .majorCode(r.getMajorCode())
                    .majorId(major != null ? major.getId() : null)
                    .credits(r.getCredits())
                    .hours(r.getHours())
                    .theoryHours(r.getTheoryHours())
                    .practiceHours(r.getPracticeHours())
                    .courseType(r.getCourseType())
                    .prerequisites(r.getPrerequisites())
                    .description(r.getDescription())
                    .build();
            toSave.add(c);
        }

        courseRepository.saveAll(toSave);
        BATCH_CACHE.remove(dto.getBatchId());

        log.info("【爱教学】批量导入成功入库 {} 门课程，操作人: {}", toSave.size(), cache.operator);

        Map<String, Object> res = new HashMap<>();
        res.put("importedCount", toSave.size());
        res.put("message", "成功批量导入 " + toSave.size() + " 门课程档案");
        return ApiResponse.success(res);
    }
}
