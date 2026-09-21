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
import com.classroom.ai.modules.course.service.CourseArchiveRules;
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
        BATCH_CACHE.entrySet().removeIf(entry -> System.currentTimeMillis() - entry.getValue().createTime > BATCH_EXPIRE_MS);
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
    public void exportCourses(List<Course> courses, OutputStream out) throws IOException {
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        out.write(bom);

        StringBuilder sb = new StringBuilder();
        sb.append("课程编码,课程名称,教研室,专业编码,学分,总学时,理论学时,实验学时,课程性质,先修课程编码,课程简介\n");

        if (courses != null) {
            for (Course c : courses) {
                String mCode = c.getMajorCode();
                sb.append(escapeCsv(c.getCourseCode())).append(",");
                sb.append(escapeCsv(c.getCourseName())).append(",");
                sb.append(escapeCsv(c.getDepartment())).append(",");
                sb.append(escapeCsv(mCode)).append(",");
                sb.append(c.getCredits() != null ? c.getCredits() : "").append(",");
                sb.append(c.getHours() != null ? c.getHours() : "").append(",");
                sb.append(c.getTheoryHours() != null ? c.getTheoryHours() : (c.getHours() != null ? c.getHours() : "")).append(",");
                sb.append(c.getPracticeHours() != null ? c.getPracticeHours() : 0).append(",");
                sb.append(escapeCsv(c.getCourseType())).append(",");
                sb.append(escapeCsv(c.getPrerequisites())).append(",");
                sb.append(escapeCsv(c.getDescription())).append("\n");
            }
        }

        out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.matches("^[=+@-].*")) {
            value = "'" + value;
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private boolean isTeachingQualityReport(List<String> headerRow) {
        if (headerRow == null) return false;
        Set<String> cleanHeaders = headerRow.stream()
                .filter(Objects::nonNull)
                .map(h -> h.replace("\uFEFF", "").trim())
                .collect(Collectors.toSet());
        return cleanHeaders.contains("质量达成评价")
                || cleanHeaders.contains("督导听课次数")
                || cleanHeaders.contains("综合均分")
                || cleanHeaders.contains("教学态度均分")
                || cleanHeaders.contains("教学效果均分")
                || (cleanHeaders.contains("任课教师") && cleanHeaders.contains("选课班级"));
    }

    private Map<String, Integer> buildHeaderMap(List<String> headerRow) {
        Map<String, Integer> map = new HashMap<>();
        if (headerRow == null) return map;

        for (int i = 0; i < headerRow.size(); i++) {
            String colName = headerRow.get(i);
            if (colName == null) continue;
            String clean = colName.replace("\uFEFF", "").trim().toLowerCase();

            if (clean.equals("课程编码") || clean.equals("课程代码") || clean.equals("coursecode") || clean.equals("course_code")) {
                map.putIfAbsent("courseCode", i);
            } else if (clean.equals("课程名称") || clean.equals("coursename") || clean.equals("course_name")) {
                map.putIfAbsent("courseName", i);
            } else if (clean.equals("教研室") || clean.equals("所属教研室") || clean.equals("开课教研室") || clean.equals("院系教研室") || clean.equals("department")) {
                map.putIfAbsent("department", i);
            } else if (clean.equals("专业编码") || clean.equals("专业代码") || clean.equals("所属专业编码") || clean.equals("所属专业") || clean.equals("majorcode") || clean.equals("major_code")) {
                map.putIfAbsent("majorCode", i);
            } else if (clean.equals("学分") || clean.equals("credits") || clean.equals("credit")) {
                map.putIfAbsent("credits", i);
            } else if (clean.equals("总学时") || clean.equals("学时") || clean.equals("hours") || clean.equals("totalhours") || clean.equals("total_hours")) {
                map.putIfAbsent("hours", i);
            } else if (clean.equals("理论学时") || clean.equals("theoryhours") || clean.equals("theory_hours")) {
                map.putIfAbsent("theoryHours", i);
            } else if (clean.equals("实验学时") || clean.equals("实践学时") || clean.equals("上机学时") || clean.equals("practicehours") || clean.equals("practice_hours")) {
                map.putIfAbsent("practiceHours", i);
            } else if (clean.equals("课程性质") || clean.equals("课程类别") || clean.equals("coursetype") || clean.equals("course_type")) {
                map.putIfAbsent("courseType", i);
            } else if (clean.equals("先修课程编码") || clean.equals("先修课程代码") || clean.equals("先修课程") || clean.equals("先修课") || clean.equals("先修关系") || clean.equals("prerequisites")) {
                map.putIfAbsent("prerequisites", i);
            } else if (clean.equals("课程简介") || clean.equals("简介") || clean.equals("课程描述") || clean.equals("description")) {
                map.putIfAbsent("description", i);
            }
        }
        return map;
    }

    private String getFieldValue(List<String> cols, Map<String, Integer> headerMap, String fieldKey, int fallbackIndex) {
        if (headerMap != null && headerMap.containsKey(fieldKey)) {
            int idx = headerMap.get(fieldKey);
            if (idx >= 0 && idx < cols.size()) {
                String val = cols.get(idx);
                return val != null ? val.trim() : "";
            }
            return "";
        }
        if (headerMap == null && fallbackIndex >= 0 && fallbackIndex < cols.size()) {
            String val = cols.get(fallbackIndex);
            return val != null ? val.trim() : "";
        }
        return "";
    }

    @Override
    public ImportPreviewVO previewImport(MultipartFile file) throws IOException {
        CourseArchiveRules.requireDirector();
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

        // 获取并检查表头
        List<String> headerRow = records.get(0);
        if (isTeachingQualityReport(headerRow)) {
            throw new IllegalArgumentException("上传文件类型不匹配：检测到您上传的是【年度教学质量分析报表】（含督导评分与听课统计数据），并非标准化【课程档案导入文件】。请在工作台顶部点击【导出课程档案 (CSV)】获取可编辑导入的课程档案，或点击【下载模板】规范填写后导入。");
        }

        // 去掉表头行
        List<List<String>> dataRows = new ArrayList<>(records);
        dataRows.remove(0); // 第一行为表头

        if (dataRows.size() > 1000) {
            throw new IllegalArgumentException("单批次导入不得超过 1000 行");
        }

        // 构建自适应表头字段映射
        Map<String, Integer> headerMap = buildHeaderMap(headerRow);
        for (String required : List.of("courseCode", "courseName", "department", "majorCode", "credits", "hours", "courseType")) {
            if (!headerMap.containsKey(required)) throw new IllegalArgumentException("缺少必填表头: " + required);
        }
        if (dataRows.isEmpty()) throw new IllegalArgumentException("上传文件无有效数据行");

        List<CourseImportRowDTO> validRows = new ArrayList<>();
        List<ImportRowError> errors = new ArrayList<>();
        Set<String> seenCodesInBatch = new HashSet<>();

        // 第一遍扫描：收集当前批次中填写的全部课程编码与名称 (用于支持批次内先修课程互相引用)
        Set<String> batchCodes = new HashSet<>();
        for (List<String> row : dataRows) {
            if (row != null && !row.isEmpty()) {
                String code = getFieldValue(row, headerMap, "courseCode", 0);
                if (!code.isEmpty()) {
                    batchCodes.add(CourseArchiveRules.referenceKey(code));
                    batchCodes.add(code.replace("《", "").replace("》", "").trim());
                }
                String name = getFieldValue(row, headerMap, "courseName", 1);
                if (!name.isEmpty()) {
                    batchCodes.add(CourseArchiveRules.referenceKey(name));
                    batchCodes.add(name.replace("《", "").replace("》", "").trim());
                }
            }
        }

        // 预加载所有有效专业编码字典缓存
        Set<String> validMajorCodes = majorRepository.findAll().stream()
                .map(m -> m.getMajorCode().toUpperCase())
                .collect(Collectors.toSet());

        // 第二遍扫描：逐行全维度校验
        int rowNumber = 2; // 标题行为第 1 行，数据行自第 2 行开始
        for (List<String> cols : dataRows) {
            validateAndParseRow(cols, rowNumber, headerMap, validMajorCodes, seenCodesInBatch, batchCodes, validRows, errors);
            rowNumber++;
        }

        int totalCount = dataRows.size();
        int errorRows = (int) errors.stream().map(ImportRowError::getRowNumber).distinct().count();
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

        BATCH_CACHE.entrySet().removeIf(entry -> System.currentTimeMillis() - entry.getValue().createTime > BATCH_EXPIRE_MS);
        BATCH_CACHE.put(batchId, cache);

        log.info("【课程导入预览】操作人: {}, 批次: {}, 总行数: {}, 有效行: {}, 错误行: {}",
                operator, batchId, totalCount, validRows.size(), errorRows);

        return ImportPreviewVO.builder()
                .batchId(batchId)
                .totalCount(totalCount)
                .successCount(validRows.size())
                .errorCount(errorRows)
                .errors(errors)
                .validRows(validRows)
                .build();
    }

    private void validateAndParseRow(List<String> cols, int rowNumber, Map<String, Integer> headerMap,
                                     Set<String> validMajorCodes, Set<String> seenCodesInBatch, Set<String> batchCodes,
                                     List<CourseImportRowDTO> validRows, List<ImportRowError> errors) {
        if (cols.size() < 7 && (headerMap == null || headerMap.size() < 4)) {
            errors.add(new ImportRowError(rowNumber, "格式错误", "列数不足，至少需提供前7项必填列"));
            return;
        }

        String courseCode = getFieldValue(cols, headerMap, "courseCode", 0);
        String courseName = getFieldValue(cols, headerMap, "courseName", 1);
        String department = getFieldValue(cols, headerMap, "department", 2);
        String majorCode = getFieldValue(cols, headerMap, "majorCode", 3);
        if (!department.isEmpty()) CourseArchiveRules.validateDepartment(department);
        String creditsStr = getFieldValue(cols, headerMap, "credits", 4);
        String hoursStr = getFieldValue(cols, headerMap, "hours", 5);
        String theoryHoursStr = getFieldValue(cols, headerMap, "theoryHours", 6);
        String practiceHoursStr = getFieldValue(cols, headerMap, "practiceHours", 7);
        String courseType = getFieldValue(cols, headerMap, "courseType", 8);
        String prerequisites = getFieldValue(cols, headerMap, "prerequisites", 9);
        String description = getFieldValue(cols, headerMap, "description", 10);

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
            if (!Double.isFinite(credits) || credits <= 0) errors.add(new ImportRowError(rowNumber, "学分", "学分必须大于 0 且为有限数值"));
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

        if (hours != null && hours > 0) {
            try {
                int[] normalized = CourseArchiveRules.normalizeHours(hours, theory, practice);
                theory = normalized[0]; practice = normalized[1];
            } catch (IllegalArgumentException e) { errors.add(new ImportRowError(rowNumber, "学时关系", e.getMessage())); }
        }
        if (!majorCode.isEmpty() && validMajorCodes.contains(majorCode.toUpperCase(Locale.ROOT))) {
            try { CourseArchiveRules.requireMajor(majorCode, majorRepository); }
            catch (IllegalArgumentException e) { errors.add(new ImportRowError(rowNumber, "专业编码", e.getMessage())); }
        }
        try { CourseArchiveRules.validatePrerequisites(prerequisites, batchCodes, courseRepository); }
        catch (IllegalArgumentException e) { errors.add(new ImportRowError(rowNumber, "先修课程", e.getMessage())); }

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
        String currentOperator = CourseArchiveRules.requireDirector().getUsername();
        if (dto == null || dto.getBatchId() == null || dto.getBatchId().isBlank())
            throw new IllegalArgumentException("批次 ID 不能为空");
        String batchId = dto.getBatchId().trim();
        ImportBatchCache cache = BATCH_CACHE.get(batchId);
        if (cache == null) throw new IllegalArgumentException("导入批次不存在或已被消费确认，请重新上传文件预览");
        // 授权先于任何缓存变更，不能让别人的请求消耗批次。
        if (!Objects.equals(cache.operator, currentOperator)) throw new ForbiddenException("越权拦截：仅批次创建者可确认导入");
        if (System.currentTimeMillis() - cache.createTime > BATCH_EXPIRE_MS) {
            BATCH_CACHE.remove(batchId, cache);
            throw new IllegalArgumentException("导入批次已超过30分钟有效期限，请重新上传预览");
        }
        if (cache.hasErrors) throw new IllegalArgumentException("当前批次存在错误，整批回滚保护禁止部分入库");
        if (cache.validRows == null || cache.validRows.isEmpty()) throw new IllegalArgumentException("批次无有效数据行");
        Set<String> batchKeys = new HashSet<>();
        for (CourseImportRowDTO row : cache.validRows) {
            batchKeys.add(CourseArchiveRules.referenceKey(row.getCourseCode()));
            batchKeys.add(CourseArchiveRules.referenceKey(row.getCourseName()));
        }
        List<Course> toSave = new ArrayList<>();
        for (CourseImportRowDTO row : cache.validRows) {
            CourseArchiveRules.validateDepartment(row.getDepartment());
            Major major = CourseArchiveRules.requireMajor(row.getMajorCode(), majorRepository);
            CourseArchiveRules.validatePrerequisites(row.getPrerequisites(), batchKeys, courseRepository);
            CourseArchiveRules.validateCredits(row.getCredits());
            int[] hours = CourseArchiveRules.normalizeHours(row.getHours(), row.getTheoryHours(), row.getPracticeHours());
            if (courseRepository.findByCourseCode(row.getCourseCode()).isPresent())
                throw new IllegalStateException("并发冲突：课程编码 " + row.getCourseCode() + " 已被写入，批次中止");
            toSave.add(Course.builder().courseCode(row.getCourseCode()).courseName(row.getCourseName())
                .department(row.getDepartment()).majorCode(major.getMajorCode()).majorId(major.getId())
                .credits(row.getCredits()).hours(row.getHours()).theoryHours(hours[0]).practiceHours(hours[1])
                .courseType(row.getCourseType()).prerequisites(row.getPrerequisites()).description(row.getDescription())
                .createdBy(currentOperator).updatedBy(currentOperator).build());
        }
        // 验证完成后原子占用，只有一个请求能进入写入阶段。
        if (!BATCH_CACHE.remove(batchId, cache)) throw new IllegalStateException("批次正在确认或已被消费");
        boolean transactionActive = org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive();
        if (transactionActive) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK) BATCH_CACHE.putIfAbsent(batchId, cache);
                    }
                });
        }
        try {
            courseRepository.saveAll(toSave);
            // 审计与课程同一事务，不吞异常，避免出现有课程却无操作记录的成功响应。
            importLogRepository.save(CourseImportLog.builder().batchId(batchId).operator(currentOperator)
                .fileName(cache.fileName).totalRows(toSave.size()).successCount(toSave.size()).errorCount(0)
                .status("SUCCESS").message("成功批量导入 " + toSave.size() + " 门课程档案").build());
            courseRepository.flush();
        } catch (RuntimeException failure) {
            if (!transactionActive) BATCH_CACHE.putIfAbsent(batchId, cache);
            throw failure;
        }
        return Map.of("importedCount", toSave.size(), "batchId", batchId, "message", "成功批量导入 " + toSave.size() + " 门课程档案");
    }
}
