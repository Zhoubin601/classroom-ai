package com.classroom.ai.modules.course.service;

import com.classroom.ai.common.exception.ForbiddenException;
import com.classroom.ai.common.exception.UnauthorizedException;
import com.classroom.ai.modules.auth.context.AuthContext;
import com.classroom.ai.modules.auth.entity.RoleEnum;
import com.classroom.ai.modules.auth.vo.UserVO;
import com.classroom.ai.modules.course.entity.Major;
import com.classroom.ai.modules.course.repository.CourseRepository;
import com.classroom.ai.modules.course.repository.MajorRepository;
import com.classroom.ai.modules.course.entity.Course;
import java.util.*;

/** 手工建档和两阶段导入共享的授权、学时及引用规则。 */
public final class CourseArchiveRules {
    private CourseArchiveRules() { }

    public static UserVO requireDirector() {
        UserVO user = AuthContext.getCurrentUser();
        if (!AuthContext.isAuthenticated() || user == null) throw new UnauthorizedException("请先登录");
        if (user.getRole() != RoleEnum.DIRECTOR) throw new ForbiddenException("仅教研室主任可以维护课程档案或导入课程");
        if (user.getDepartment() == null || user.getDepartment().isBlank())
            throw new ForbiddenException("主任教研室未配置，拒绝操作");
        return user;
    }

    public static void validateDepartment(String department) {
        UserVO user = requireDirector();
        if (department == null || !user.getDepartment().trim().equals(department.trim()))
            throw new ForbiddenException("仅可维护本教研室课程与专业，禁止跨教研室操作");
    }

    public static Major requireMajor(String code, MajorRepository repository) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("专业编码不能为空");
        Major major = repository.findByMajorCode(code.trim().toUpperCase(Locale.ROOT))
            .orElseThrow(() -> new IllegalArgumentException("未知的专业编码: " + code));
        String department = requireDirector().getDepartment().trim();
        boolean lead = department.equals(major.getDepartment());
        if (!lead && repository.findByDepartment(department).stream()
                .noneMatch(m -> m.getMajorCode().equals(major.getMajorCode())))
            throw new ForbiddenException("专业未关联当前教研室，禁止跨专业建档或导入");
        return major;
    }

    /** 只缺一个分项时按差额补齐；两项均缺时默认为全理论学时。 */
    public static int[] normalizeHours(Integer total, Integer theory, Integer practice) {
        if (total == null || total <= 0) throw new IllegalArgumentException("总学时必须大于 0");
        if (theory == null && practice == null) { theory = total; practice = 0; }
        else if (theory == null) { theory = total - practice; }
        else if (practice == null) { practice = total - theory; }
        if (theory < 0 || practice < 0 || (long) theory + practice != total)
            throw new IllegalArgumentException("理论学时(" + theory + ") + 实验学时(" + practice + ") 必须等于总学时(" + total + ")，且分项不能为负数");
        return new int[]{theory, practice};
    }

    public static void validateCredits(Double credits) {
        if (credits == null || !Double.isFinite(credits) || credits <= 0)
            throw new IllegalArgumentException("学分必须为大于 0 的有限数值");
    }

    public static String referenceKey(String value) {
        return value == null ? "" : value.replace("《", "").replace("》", "").trim().toUpperCase(Locale.ROOT);
    }

    /** 保留已有导入格式的编码/名称引用兼容，同时在确认时重新核对。 */
    public static void validatePrerequisites(String prerequisites, Set<String> batchKeys, CourseRepository courses) {
        for (String part : prerequisiteTokens(prerequisites)) {
            String value = part.trim();
            if (value.isEmpty()) continue;
            String clean = value.replace("《", "").replace("》", "").trim();
            if (batchKeys.contains(referenceKey(value))) continue;
            if (courses.findByCourseCode(value).isPresent() || courses.findByCourseCode(clean).isPresent()) continue;
            Map<String,Course> matches = new LinkedHashMap<>();
            for (String name : new LinkedHashSet<>(List.of(value, clean, "《" + clean + "》")))
                for (Course match : courses.findAllByCourseName(name)) matches.put(referenceKey(match.getCourseCode()), match);
            if (matches.size() > 1) throw new IllegalArgumentException("先修课程名称有歧义，请使用课程编码: " + value);
            if (matches.size() == 1) continue;
            throw new IllegalArgumentException("引用的先修课程编码 [" + value + "] 在数据库及当前导入批次中均不存在");
        }
    }

    public static List<String> prerequisiteTokens(String value) {
        if (value == null || value.isBlank() || "无".equals(value.trim())) return List.of();
        return Arrays.stream(value.split("[,;，；、]")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /** Existing unknown text is preserved; new/changed references must already exist. */
    public static void validateDependencyGraph(List<Course> proposed, List<Course> persisted) {
        Map<String,Course> nodes = new LinkedHashMap<>();
        persisted.forEach(course -> nodes.put(referenceKey(course.getCourseCode()), course));
        proposed.forEach(course -> nodes.put(referenceKey(course.getCourseCode()), course));
        Map<String,Set<String>> edges = new HashMap<>();
        for (var entry : nodes.entrySet()) {
            Set<String> dependencies = new LinkedHashSet<>();
            for (String token : prerequisiteTokens(entry.getValue().getPrerequisites())) {
                String key = referenceKey(token);
                Course byCode = nodes.get(key);
                List<Course> targets = byCode != null ? List.of(byCode) : nodes.values().stream()
                        .filter(course -> referenceKey(course.getCourseName()).equals(key)).toList();
                if (targets.size() > 1 && proposed.contains(entry.getValue()))
                    throw new IllegalArgumentException("先修课程名称有歧义，请使用课程编码: " + token);
                for (Course target : targets) dependencies.add(referenceKey(target.getCourseCode()));
            }
            edges.put(entry.getKey(), dependencies);
        }
        // Reject cycles reachable from changed courses; unrelated legacy graph defects do not block edits.
        for (Course course : proposed)
            visitDependency(referenceKey(course.getCourseCode()), edges, new LinkedHashSet<>(), new HashSet<>());
    }

    private static void visitDependency(String code, Map<String,Set<String>> edges, Set<String> path, Set<String> done) {
        if (path.contains(code)) throw new IllegalArgumentException("先修课程不能引用自身或形成循环: " + String.join(" → ", path) + " → " + code);
        if (done.contains(code)) return;
        path.add(code);
        for (String next : edges.getOrDefault(code, Set.of())) visitDependency(next, edges, path, done);
        path.remove(code); done.add(code);
    }

    public static void protectReferencedCourse(Course original, String nextName, List<Course> persisted) {
        if (Objects.equals(original.getCourseName(), nextName)) return;
        String oldName = referenceKey(original.getCourseName());
        boolean referencedByName = persisted.stream().filter(c -> !Objects.equals(c.getId(), original.getId()))
                .flatMap(c -> prerequisiteTokens(c.getPrerequisites()).stream()).anyMatch(token -> referenceKey(token).equals(oldName));
        if (referencedByName)
            throw new IllegalStateException("课程名称被先修关系引用，请先将引用改为稳定课程编码，再修改名称");
    }

    public static String canonicalPrerequisites(String value, List<Course> persisted) {
        List<String> refs = new ArrayList<>();
        for (String token : prerequisiteTokens(value)) {
            String key = referenceKey(token);
            List<Course> targets = persisted.stream().filter(c -> referenceKey(c.getCourseCode()).equals(key)).toList();
            if (targets.isEmpty()) targets = persisted.stream().filter(c -> referenceKey(c.getCourseName()).equals(key)).toList();
            if (targets.size() != 1) throw new IllegalArgumentException("先修课程不存在或名称有歧义，请使用课程编码: " + token);
            refs.add(targets.get(0).getCourseCode());
        }
        return String.join(",", new LinkedHashSet<>(refs));
    }
}
