package com.classroom.ai;

import com.classroom.ai.common.ApiExceptionHandler;
import com.classroom.ai.modules.course.controller.CourseController;
import com.classroom.ai.modules.course.service.CourseService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiExceptionHandlerTest {
    @Test void invalidInputUsesJsonAndClientStatus() throws Exception {
        var service = mock(CourseService.class);
        when(service.getCourseById(99L)).thenThrow(new IllegalArgumentException("Invalid course"));
        MockMvcBuilders.standaloneSetup(new CourseController(service)).setControllerAdvice(new ApiExceptionHandler()).build()
                .perform(get("/api/v1/courses/99"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Invalid course"));
    }

    @Test void databaseConstraintErrorsDoNotExposeSqlOrReturnServerError() throws Exception {
        var service = mock(CourseService.class);
        doThrow(new DataIntegrityViolationException("private SQL details")).when(service).deleteCourse(1L);
        MockMvcBuilders.standaloneSetup(new CourseController(service)).setControllerAdvice(new ApiExceptionHandler()).build()
                .perform(delete("/api/v1/courses/1"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("数据缺少必填字段、重复或仍被其他记录引用"));
    }

    @Test void malformedJsonUsesResponseEnvelope() throws Exception {
        MockMvcBuilders.standaloneSetup(new CourseController(mock(CourseService.class)))
                .setControllerAdvice(new ApiExceptionHandler()).build()
                .perform(post("/api/v1/courses").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
    }
}
