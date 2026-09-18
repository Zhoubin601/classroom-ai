package com.classroom.ai.controller;

import com.classroom.ai.common.ApiResponse;
import com.classroom.ai.dto.StudentDTO;
import com.classroom.ai.entity.Student;
import com.classroom.ai.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;

    /**
     * 1. 查询所有学生列表（供前端管理表格展示）
     */
    @GetMapping("/list")
    public ApiResponse<List<Student>> listStudents() {
        List<Student> list = studentService.getAllStudents();
        return ApiResponse.success(list);
    }

    /**
     * 2. 查询单个学生详情
     */
    @GetMapping("/{studentId}")
    public ApiResponse<Student> getStudent(@PathVariable("studentId") String studentId) {
        Student student = studentService.getStudentByStudentId(studentId);
        if (student == null) {
            return ApiResponse.error(404, "Student not found");
        }
        return ApiResponse.success(student);
    }

    /**
     * 3. 添加或更新学生基础档案
     */
    @PostMapping
    public ApiResponse<Student> saveStudent(@RequestBody StudentDTO dto) {
        Student saved = studentService.saveStudent(dto);
        return ApiResponse.success("Student saved successfully", saved);
    }

    /**
     * 4. 删除学生档案（级联删除人脸特征及缓存，并同步更新开课班额）
     */
    @DeleteMapping("/{studentId}")
    public ApiResponse<Boolean> deleteStudent(@PathVariable("studentId") String studentId) {
        boolean deleted = studentService.deleteStudent(studentId);
        return ApiResponse.success("Student deleted successfully", deleted);
    }

    /**
     * 5. 调配学生所属班级 (并同步联动涉及开课班级实际人数)
     */
    @PutMapping("/{studentId}/class")
    public ApiResponse<Student> updateStudentClass(@PathVariable("studentId") String studentId, @RequestParam String className) {
        Student updated = studentService.updateStudentClass(studentId, className);
        return ApiResponse.success("学生班级调整成功", updated);
    }
}
