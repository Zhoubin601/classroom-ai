package com.classroom.ai.modules.course.service;

import com.classroom.ai.common.exception.ForbiddenException;
import com.classroom.ai.common.exception.UnauthorizedException;
import com.classroom.ai.modules.auth.context.AuthContext;
import com.classroom.ai.modules.auth.entity.RoleEnum;
import com.classroom.ai.modules.auth.vo.UserVO;
import com.classroom.ai.modules.course.entity.Major;
import com.classroom.ai.modules.course.repository.CourseRepository;
import com.classroom.ai.modules.course.repository.MajorRepository;
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
        validateDepartment(major.getDepartment());
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
        return value.replace("《", "").replace("》", "").trim().toUpperCase(Locale.ROOT);
    }

    /** 保留已有导入格式的编码/名称引用兼容，同时在确认时重新核对。 */
    public static void validatePrerequisites(String prerequisites, Set<String> batchKeys, CourseRepository courses) {
        if (prerequisites == null || prerequisites.isBlank()) return;
        for (String part : prerequisites.split("[,;，；、]")) {
            String value = part.trim();
            if (value.isEmpty()) continue;
            String clean = value.replace("《", "").replace("》", "").trim();
            if (batchKeys.contains(referenceKey(value))) continue;
            if (courses.findByCourseCode(value).isPresent() || courses.findByCourseCode(clean).isPresent()
                || courses.findByCourseName(value).isPresent() || courses.findByCourseName(clean).isPresent()
                || courses.findByCourseName("《" + clean + "》").isPresent()) continue;
            throw new IllegalArgumentException("引用的先修课程编码 [" + value + "] 在数据库及当前导入批次中均不存在");
        }
    }
}
