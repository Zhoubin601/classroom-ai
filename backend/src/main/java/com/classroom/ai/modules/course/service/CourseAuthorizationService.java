package com.classroom.ai.modules.course.service;

import com.classroom.ai.common.exception.ForbiddenException;
import com.classroom.ai.common.exception.UnauthorizedException;
import com.classroom.ai.modules.auth.context.AuthContext;
import com.classroom.ai.modules.auth.entity.RoleEnum;
import com.classroom.ai.modules.auth.vo.UserVO;
import com.classroom.ai.modules.course.entity.Course;
import com.classroom.ai.modules.course.entity.CourseOffering;
import com.classroom.ai.modules.course.entity.CourseOfferingTeacher;
import com.classroom.ai.modules.course.repository.CourseOfferingRepository;
import com.classroom.ai.modules.course.repository.CourseOfferingTeacherRepository;
import com.classroom.ai.modules.course.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 实验二核心教务统一授权服务 (CourseAuthorizationService)
 * 践行 Deny-by-Default 原则：
 * 1. 业务接口统一要求登录（未登录抛出 401 Unauthorized）；
 * 2. 主任管理所属教研室；教师访问本人关联课程；督导读取授权专业；
 * 3. 课程/班次无授权、专业缺失等情况默认不放行（抛出 403 Forbidden）；
 * 4. 列表、详情、历史、草稿、排课及相关资源入口统一纳管，避免绕过列表直接访问 ID 越权。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseAuthorizationService {

    private final CourseRepository courseRepository;
    private final CourseOfferingRepository offeringRepository;
    private final CourseOfferingTeacherRepository offeringTeacherRepository;

    public UserVO requireCurrentUser() {
        if (!AuthContext.isAuthenticated() || AuthContext.getCurrentUser() == null) {
            throw new UnauthorizedException("未登录或登录令牌已过期，请重新登录");
        }
        return AuthContext.getCurrentUser();
    }

    public void validateCourseRead(Course course) {
        if (course == null) {
            throw new IllegalArgumentException("待校验课程档案不能为空");
        }
        UserVO user = requireCurrentUser();
        RoleEnum role = user.getRole();

        if (role == RoleEnum.DIRECTOR) {
            checkDirectorCourseAccess(user, course);
        } else if (role == RoleEnum.TEACHER) {
            checkTeacherCourseAccess(user, course);
        } else if (role == RoleEnum.SUPERVISOR) {
            checkSupervisorCourseAccess(user, course);
        } else {
            throw new ForbiddenException("未知用户角色，默认拒绝访问课程档案");
        }
    }

    public void validateCourseRead(Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定课程 (ID: " + courseId + ")"));
        validateCourseRead(course);
    }

    public void validateCourseWrite(Course course) {
        if (course == null) {
            throw new IllegalArgumentException("待校验课程档案不能为空");
        }
        UserVO user = requireCurrentUser();
        RoleEnum role = user.getRole();

        if (role == RoleEnum.SUPERVISOR) {
            throw new ForbiddenException("教学督导为只读审查角色，严禁执行课程写入/修改操作");
        } else if (role == RoleEnum.DIRECTOR) {
            checkDirectorCourseAccess(user, course);
        } else if (role == RoleEnum.TEACHER) {
            checkTeacherCourseAccess(user, course);
        } else {
            throw new ForbiddenException("未知用户角色，拒绝执行课程写操作");
        }
    }

    public void validateCourseWrite(Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定课程 (ID: " + courseId + ")"));
        validateCourseWrite(course);
    }

    public void validateOfferingRead(CourseOffering offering) {
        if (offering == null) {
            throw new IllegalArgumentException("待校验开课班次不能为空");
        }
        UserVO user = requireCurrentUser();
        RoleEnum role = user.getRole();

        if (role == RoleEnum.DIRECTOR) {
            if (offering.getCourse() != null) {
                checkDirectorCourseAccess(user, offering.getCourse());
            }
        } else if (role == RoleEnum.TEACHER) {
            checkTeacherOfferingAccess(user, offering);
        } else if (role == RoleEnum.SUPERVISOR) {
            checkSupervisorOfferingAccess(user, offering);
        } else {
            throw new ForbiddenException("未知角色，拒绝访问该开课班次");
        }
    }

    public void validateOfferingWrite(CourseOffering offering) {
        if (offering == null) {
            throw new IllegalArgumentException("待校验开课班次不能为空");
        }
        UserVO user = requireCurrentUser();
        RoleEnum role = user.getRole();

        if (role == RoleEnum.SUPERVISOR) {
            throw new ForbiddenException("教学督导无权对开课班次进行编辑或排课操作");
        } else if (role == RoleEnum.DIRECTOR) {
            if (offering.getCourse() != null) {
                checkDirectorCourseAccess(user, offering.getCourse());
            }
        } else if (role == RoleEnum.TEACHER) {
            checkTeacherOfferingAccess(user, offering);
        } else {
            throw new ForbiddenException("未知角色，拒绝修改开课班次");
        }
    }

    public List<Course> filterCourses(List<Course> list) {
        if (list == null || list.isEmpty()) return Collections.emptyList();
        UserVO user = requireCurrentUser();
        RoleEnum role = user.getRole();

        if (role == RoleEnum.DIRECTOR) {
            String dirDept = user.getDepartment() != null ? user.getDepartment().trim() : "";
            return list.stream()
                    .filter(c -> c.getDepartment() != null && c.getDepartment().trim().equals(dirDept))
                    .collect(Collectors.toList());
        } else if (role == RoleEnum.TEACHER) {
            return list.stream()
                    .filter(c -> isTeacherAssociatedWithCourse(user, c))
                    .collect(Collectors.toList());
        } else if (role == RoleEnum.SUPERVISOR) {
            Set<String> authMajors = parseAuthorizedMajors(user);
            if (authMajors.isEmpty()) return Collections.emptyList();
            return list.stream()
                    .filter(c -> c.getMajorCode() != null && authMajors.contains(c.getMajorCode().trim().toUpperCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    public List<CourseOffering> filterOfferings(List<CourseOffering> list) {
        if (list == null || list.isEmpty()) return Collections.emptyList();
        UserVO user = requireCurrentUser();
        RoleEnum role = user.getRole();

        if (role == RoleEnum.DIRECTOR) {
            String dirDept = user.getDepartment() != null ? user.getDepartment().trim() : "";
            return list.stream()
                    .filter(o -> dirDept.isEmpty() || (o.getCourse() != null && dirDept.equalsIgnoreCase(o.getCourse().getDepartment())))
                    .collect(Collectors.toList());
        } else if (role == RoleEnum.TEACHER) {
            return list.stream()
                    .filter(o -> isTeacherAssociatedWithOffering(user, o))
                    .collect(Collectors.toList());
        } else if (role == RoleEnum.SUPERVISOR) {
            Set<String> authMajors = parseAuthorizedMajors(user);
            if (authMajors.isEmpty()) return Collections.emptyList();
            return list.stream()
                    .filter(o -> {
                        String mCode = o.getMajorCode();
                        if ((mCode == null || mCode.isBlank()) && o.getCourse() != null) {
                            mCode = o.getCourse().getMajorCode();
                        }
                        return mCode != null && authMajors.contains(mCode.trim().toUpperCase());
                    })
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private void checkDirectorCourseAccess(UserVO director, Course course) {
        String dirDept = director.getDepartment();
        if (dirDept == null || dirDept.isBlank()) {
            throw new ForbiddenException("教研室主任部门档案未配置，默认拒绝访问");
        }
        if (course.getDepartment() == null || !dirDept.trim().equals(course.getDepartment().trim())) {
            throw new ForbiddenException("越权拦截：教研室主任仅可管理本教研室 (" + dirDept + ") 的课程档案");
        }
    }

    private void checkTeacherCourseAccess(UserVO teacher, Course course) {
        if (!isTeacherAssociatedWithCourse(teacher, course)) {
            throw new ForbiddenException("越权拦截：任课教师仅可访问本人主讲或关联的课程档案 (课程: " + course.getCourseName() + ")");
        }
    }

    private void checkSupervisorCourseAccess(UserVO supervisor, Course course) {
        String majorCode = course.getMajorCode();
        if (majorCode == null || majorCode.isBlank()) {
            throw new ForbiddenException("越权拦截：课程专业缺失，督导角色默认不予放行 (ID: " + course.getId() + ")");
        }
        Set<String> authMajors = parseAuthorizedMajors(supervisor);
        if (!authMajors.contains(majorCode.trim().toUpperCase())) {
            throw new ForbiddenException("越权拦截：督导无权直接访问未授权专业课程档案 (" + majorCode + ")");
        }
    }

    private void checkTeacherOfferingAccess(UserVO teacher, CourseOffering offering) {
        if (!isTeacherAssociatedWithOffering(teacher, offering)) {
            throw new ForbiddenException("越权拦截：任课教师仅可操作本人所属开课班次 (班次ID: " + offering.getId() + ")");
        }
    }

    private void checkSupervisorOfferingAccess(UserVO supervisor, CourseOffering offering) {
        String majorCode = offering.getMajorCode();
        if ((majorCode == null || majorCode.isBlank()) && offering.getCourse() != null) {
            majorCode = offering.getCourse().getMajorCode();
        }
        if (majorCode == null || majorCode.isBlank()) {
            throw new ForbiddenException("越权拦截：开课班次所属专业缺失，督导默认不予放行");
        }
        Set<String> authMajors = parseAuthorizedMajors(supervisor);
        if (!authMajors.contains(majorCode.trim().toUpperCase())) {
            throw new ForbiddenException("越权拦截：督导无权查阅未授权专业 (" + majorCode + ") 的开课班次");
        }
    }

    private boolean isTeacherAssociatedWithCourse(UserVO teacher, Course course) {
        String tCode = teacher.getTeacherCode();
        String tName = teacher.getRealName();
        if (tName != null && course.getTeacherName() != null && course.getTeacherName().contains(tName)) {
            return true;
        }
        if (course.getId() != null) {
            List<CourseOffering> offerings = offeringRepository.findByCourseId(course.getId());
            for (CourseOffering off : offerings) {
                if (isTeacherAssociatedWithOffering(teacher, off)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isTeacherAssociatedWithOffering(UserVO teacher, CourseOffering offering) {
        String tCode = teacher.getTeacherCode();
        String tName = teacher.getRealName();

        if (tCode != null && tCode.equalsIgnoreCase(offering.getTeacherCode())) {
            return true;
        }
        if (tName != null && offering.getTeacherName() != null && offering.getTeacherName().contains(tName)) {
            return true;
        }
        if (offering.getId() != null && tCode != null) {
            List<CourseOfferingTeacher> assistants = offeringTeacherRepository.findByOfferingId(offering.getId());
            for (CourseOfferingTeacher cot : assistants) {
                if (tCode.equalsIgnoreCase(cot.getTeacherCode())
                        || (tName != null && tName.equalsIgnoreCase(cot.getTeacherName()))) {
                    return true;
                }
            }
        }
        return false;
    }

    private Set<String> parseAuthorizedMajors(UserVO user) {
        String authorizedMajors = user.getAuthorizedMajors();
        if (authorizedMajors == null || authorizedMajors.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(authorizedMajors.split(";"))
                .map(String::trim)
                .map(String::toUpperCase)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }
}
