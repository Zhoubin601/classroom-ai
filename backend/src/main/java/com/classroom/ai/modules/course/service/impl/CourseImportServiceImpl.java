package com.classroom.ai.modules.course.service.impl;

import com.classroom.ai.common.exception.ForbiddenException;
import com.classroom.ai.modules.auth.context.AuthContext;
import com.classroom.ai.modules.auth.vo.UserVO;
import com.classroom.ai.modules.course.dto.CourseImportRowDTO;
import com.classroom.ai.modules.course.dto.ImportConfirmDTO;
import com.classroom.ai.modules.course.entity.Course;
import com.classroom.ai.modules.course.entity.CourseImportLog;
import com.classroom.ai.modules.course.entity.Major;
import com.classroom.ai.modules.course.repository.CourseImportLogRepository;
import com.classroom.ai.modules.course.repository.CourseRepository;
import com.classroom.ai.modules.course.repository.MajorRepository;
import com.classroom.ai.modules.course.service.CourseImportService;
import com.classroom.ai.modules.course.util.CsvParserUtil;
import com.classroom.ai.modules.course.vo.ImportPreviewVO;
import com.classroom.ai.modules.course.vo.ImportRowError;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseImportServiceImpl implements CourseImportService {

    private final CourseRepository courseRepository;
    private final MajorRepository majorRepository;
    private final CourseImportLogRepository importLogRepository;

    // 内存中维护与操作人绑定的导入预览批次，30 分钟过期
    private static final long BATCH_EXPIRE_MS = 30 * 60 * 1000L;
    private static final Map<String, ImportBatchCache> BATCH_CACHE = new ConcurrentHashMap<>();

    public static class ImportBatchCache {
        public String batchId;
        public String operator;
        public String fileName;
        public long createTime;
        public List<CourseImportRowDTO> validRows;
        public boolean hasErrors;
        public Set<String> batchCodes;
    }

    // 提供给单元测试或维护使用的访问/清理钩子
    public static void clearBatchCache() {
        BATCH_CACHE.clear();
    }

    public static void putBatchCache(String batchId, ImportBatchCache cache) {
        BATCH_CACHE.put(batchId, cache);
    }

    public static ImportBatchCache getBatchCache(String batchId) {
        return BATCH_CACHE.get(batchId);
    }

    @Override
    public void downloadTemplate(OutputStream out) throws IOException {
        // 写入 UTF-8 BOM 字节以兼容 Excel /各类中文编辑器
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        out.write(bom);

        String content = "课程编码,课程名称,教研室,专业编码,学分,总学时,理论学时,实验学时,课程性质,先修课程编码,课程简介\n" +
                "CS3001,软件项目管理,软件工程教研室,SE,3.0,48,36,12,专业核心课,CS1001;CS2001,系统讲授现代软件工程项目管理实践。\n" +
                "CS3002,数据库系统概论,计算机科学教研室,CS,4.0,64,48,16,专业核心课,CS1002,全面介绍关系型数据库与SQL核心原理。\n";

        out.write(content.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    @Override
    public ImportPreviewVO previewImport(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("文件大小超出 5MB 限制");
        }

        List<List<String>> records = CsvParserUtil.parseCsv(file.getInputStream());
        if (records.isEmpty()) {
            throw new IllegalArgumentException("上传文件为空或无有效数据行");
        }

        // 去掉表头行
        List<List<String>> dataRows = new ArrayList<>(records);
        if (!dataRows.isEmpty()) {
            dataRows.remove(0); // 第一行为表头
        }

        if (dataRows.size() > 1000) {
            throw new IllegalArgumentException("单批次导入不得超过 1000 行");
        }

        List<CourseImportRowDTO> validRows = new ArrayList<>();
        List<ImportRowError> errors = new ArrayList<>();
        Set<String> seenCodesInBatch = new HashSet<>();

        // 第一遍扫描：收集当前批次中填写的全部课程编码 (用于支持批次内先修课程互相引用)
        Set<String> batchCodes = new HashSet<>();
        for (List<String> row : dataRows) {
            if (row != null && !row.isEmpty() && row.get(0) != null && !row.get(0).trim().isEmpty()) {
                batchCodes.add(row.get(0).trim());
            }
        }

        // 预加载所有有效专业编码字典缓存
        Set<String> validMajorCodes = majorRepository.findAll().stream()
                .map(m -> m.getMajorCode().toUpperCase())
                .collect(Collectors.toSet());

        // 第二遍扫描：逐行全维度校验
        int rowNumber = 2; // 标题行为第 1 行，数据行自第 2 行开始
        for (List<String> cols : dataRows) {
            validateAndParseRow(cols, rowNumber, validMajorCodes, seenCodesInBatch, batchCodes, validRows, errors);
            rowNumber++;
        }

        int totalCount = validRows.size() + errors.size();
        String batchId = UUID.randomUUID().toString();
        String operator = AuthContext.isAuthenticated() && AuthContext.getCurrentUser() != null
                ? AuthContext.getCurrentUser().getUsername() : "ANONYMOUS";

        ImportBatchCache cache = new ImportBatchCache();
        cache.batchId = batchId;
        cache.operator = operator;
        cache.fileName = file.getOriginalFilename();
        cache.createTime = System.currentTimeMillis();
        cache.validRows = validRows;
        cache.hasErrors = !errors.isEmpty();
        cache.batchCodes = batchCodes;

        BATCH_CACHE.put(batchId, cache);

        log.info("【课程导入预览】操作人: {}, 批次: {}, 总行数: {}, 有效行: {}, 错误行: {}",
                operator, batchId, totalCount, validRows.size(), errors.size());

        return ImportPreviewVO.builder()
                .batchId(batchId)
                .totalCount(totalCount)
                .successCount(validRows.size())
                .errorCount(errors.size())
                .errors(errors)
                .validRows(validRows)
                .build();
    }

    private void validateAndParseRow(List<String> cols, int rowNumber, Set<String> validMajorCodes,
                                     Set<String> seenCodesInBatch, Set<String> batchCodes,
                                     List<CourseImportRowDTO> validRows, List<ImportRowError> errors) {
        if (cols.size() < 7) {
            errors.add(new ImportRowError(rowNumber, "格式错误", "列数不足，至少需提供前7项必填列"));
            return;
        }

        String courseCode = cols.get(0).trim();
        String courseName = cols.get(1).trim();
        String department = cols.get(2).trim();
        String majorCode = cols.get(3).trim();
        String creditsStr = cols.get(4).trim();
        String hoursStr = cols.get(5).trim();
        String theoryHoursStr = cols.size() > 6 ? cols.get(6).trim() : "";
        String practiceHoursStr = cols.size() > 7 ? cols.get(7).trim() : "";
        String courseType = cols.size() > 8 ? cols.get(8).trim() : "";
        String prerequisites = cols.size() > 9 ? cols.get(9).trim() : "";
        String description = cols.size() > 10 ? cols.get(10).trim() : "";

        // 1. 必填缺项校验
        if (courseCode.isEmpty()) errors.add(new ImportRowError(rowNumber, "课程编码", "课程编码不能为空"));
        if (courseName.isEmpty()) errors.add(new ImportRowError(rowNumber, "课程名称", "课程名称不能为空"));
        if (department.isEmpty()) errors.add(new ImportRowError(rowNumber, "教研室", "教研室不能为空"));
        if (majorCode.isEmpty()) errors.add(new ImportRowError(rowNumber, "专业编码", "专业编码不能为空"));
        if (courseType.isEmpty()) errors.add(new ImportRowError(rowNumber, "课程性质", "课程性质不能为空"));

        // 2. 编码唯一性校验 (批次内防重 & 数据库防重)
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
            try {
                theory = Integer.parseInt(theoryHoursStr);
                if (theory < 0) errors.add(new ImportRowError(rowNumber, "理论学时", "理论学时不能为负数"));
            } catch (Exception e) {
                errors.add(new ImportRowError(rowNumber, "理论学时", "理论学时必须为整数"));
            }
        }
        if (!practiceHoursStr.isEmpty()) {
            try {
                practice = Integer.parseInt(practiceHoursStr);
                if (practice < 0) errors.add(new ImportRowError(rowNumber, "实验学时", "实验学时不能为负数"));
            } catch (Exception e) {
                errors.add(new ImportRowError(rowNumber, "实验学时", "实验学时必须为整数"));
            }
        }

        if (theory == null && practice == null && hours != null) {
            theory = hours;
            practice = 0;
        } else if (hours != null && theory != null && practice != null) {
            if (theory + practice != hours) {
                errors.add(new ImportRowError(rowNumber, "学时关系", "理论学时(" + theory + ") + 实验学时(" + practice + ") 必须等于总学时(" + hours + ")"));
            }
        }

        // 5. 先修课程引用校验 (支持数据库中已有课程 OR 当前批次内课程)
        if (!prerequisites.isEmpty()) {
            String[] prereqParts = prerequisites.split("[,;]");
            for (String p : prereqParts) {
                String cleanP = p.trim();
                if (cleanP.isEmpty()) continue;
                // 校验先修课程是否存在于数据库 或 存在于当前批次
                boolean existsInDb = courseRepository.findByCourseCode(cleanP).isPresent();
                boolean existsInBatch = batchCodes.contains(cleanP);
                if (!existsInDb && !existsInBatch) {
                    errors.add(new ImportRowError(rowNumber, "先修课程", "引用的先修课程编码 [" + cleanP + "] 在数据库及当前导入批次中均不存在"));
                }
            }
        }

        // 若本行无错误，加入有效数据行
        if (errors.stream().noneMatch(e -> e.getRowNumber() == rowNumber)) {
            validRows.add(CourseImportRowDTO.builder()
                    .rowNumber(rowNumber)
                    .courseCode(courseCode)
                    .courseName(courseName)
                    .department(department)
                    .majorCode(majorCode.toUpperCase())
                    .credits(credits)
                    .hours(hours)
                    .theoryHours(theory != null ? theory : hours)
                    .practiceHours(practice != null ? practice : 0)
                    .courseType(courseType)
                    .prerequisites(prerequisites)
                    .description(description)
                    .build());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> confirmImport(ImportConfirmDTO dto) {
        if (dto == null || dto.getBatchId() == null || dto.getBatchId().trim().isEmpty()) {
            throw new IllegalArgumentException("批次 ID 不能为空");
        }

        // 原子移除批次，确保一次性消费，防止重复确认
        ImportBatchCache cache = BATCH_CACHE.remove(dto.getBatchId().trim());
        if (cache == null) {
            throw new IllegalArgumentException("导入批次不存在或已被消费确认，请重新上传文件预览");
        }

        // 校验批次 30 分钟有效期
        if (System.currentTimeMillis() - cache.createTime > BATCH_EXPIRE_MS) {
            throw new IllegalArgumentException("导入批次已超过30分钟有效期限，已失效，请重新上传预览");
        }

        // 校验批次是否存在错误 (整批回滚保护原则)
        if (cache.hasErrors) {
            throw new IllegalArgumentException("当前批次存在校验错误行，系统实行整批回滚保护，禁止部分入库");
        }

        // 批次与操作者绑定校验 (防越权确认)
        String currentOperator = AuthContext.isAuthenticated() && AuthContext.getCurrentUser() != null
                ? AuthContext.getCurrentUser().getUsername() : "ANONYMOUS";
        if (!"ANONYMOUS".equals(cache.operator) && !cache.operator.equals(currentOperator)) {
            throw new ForbiddenException("越权拦截：仅当前批次的创建者 [" + cache.operator + "] 可确认导入，当前登录操作人: [" + currentOperator + "]");
        }

        // 重新核对数据库防并发插入与先修课程再验证
        List<Course> toSave = new ArrayList<>();
        for (CourseImportRowDTO r : cache.validRows) {
            if (courseRepository.findByCourseCode(r.getCourseCode()).isPresent()) {
                throw new IllegalStateException("并发冲突：课程编码 " + r.getCourseCode() + " 已被其他操作者写入，批次中止");
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

        // 整批保存 (Transactional 保证异常时整批回滚，不会留下半批数据)
        courseRepository.saveAll(toSave);

        // 写入审计记录
        try {
            CourseImportLog logRecord = CourseImportLog.builder()
                    .batchId(dto.getBatchId())
                    .operator(cache.operator)
                    .fileName(cache.fileName)
                    .totalRows(cache.validRows.size())
                    .successCount(toSave.size())
                    .errorCount(0)
                    .status("SUCCESS")
                    .message("成功批量导入 " + toSave.size() + " 门课程档案")
                    .build();
            importLogRepository.save(logRecord);
        } catch (Exception e) {
            log.warn("保存导入审计日志异常: {}", e.getMessage());
        }

        log.info("【US-01 课程导入完成】批次: {}, 成功入库: {} 门, 操作者: {}",
                dto.getBatchId(), toSave.size(), cache.operator);

        Map<String, Object> res = new HashMap<>();
        res.put("importedCount", toSave.size());
        res.put("batchId", dto.getBatchId());
        res.put("message", "成功批量导入 " + toSave.size() + " 门课程档案");
        return res;
    }
}
