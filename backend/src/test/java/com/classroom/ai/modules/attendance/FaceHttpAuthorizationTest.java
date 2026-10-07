package com.classroom.ai.modules.attendance;

import com.classroom.ai.controller.FaceController;
import com.classroom.ai.modules.auth.entity.*;
import com.classroom.ai.modules.auth.repository.UserAccountRepository;
import com.classroom.ai.modules.auth.security.*;
import com.classroom.ai.modules.auth.vo.UserVO;
import com.classroom.ai.service.FaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FaceController.class)
@Import({SecurityConfig.class,JwtAuthenticationFilter.class})
class FaceHttpAuthorizationTest {
    @Autowired MockMvc mvc;
    @MockBean FaceService faces;
    @MockBean JwtTokenProvider tokens;
    @MockBean UserAccountRepository accounts;
    private void login(RoleEnum role) {
        when(tokens.validateToken("synthetic")).thenReturn(true);
        when(tokens.parseUserFromToken("synthetic")).thenReturn(UserVO.builder().username("synthetic").build());
        when(accounts.findByUsername("synthetic")).thenReturn(Optional.of(UserAccount.builder().username("synthetic").role(role).build()));
    }
    @Test void anonymousHasNoFaceLibraryAccess() throws Exception {
        mvc.perform(get("/api/face/all")).andExpect(status().isUnauthorized());verifyNoInteractions(faces);
    }
    @Test void teacherAndSupervisorCannotReadRegisterSearchOrDeleteFaces() throws Exception {
        for(var role:List.of(RoleEnum.TEACHER,RoleEnum.SUPERVISOR)) {
            login(role);
            mvc.perform(get("/api/face/all").header("Authorization","Bearer synthetic")).andExpect(status().isForbidden());
            mvc.perform(get("/api/face/S1").header("Authorization","Bearer synthetic")).andExpect(status().isForbidden());
            for(var route:List.of("register","search","launch-register"))
                mvc.perform(post("/api/face/"+route).header("Authorization","Bearer synthetic").contentType(MediaType.APPLICATION_JSON).content("{}"))
                        .andExpect(status().isForbidden());
            mvc.perform(delete("/api/face/S1").header("Authorization","Bearer synthetic")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(faces);
    }
    @Test void directorCanReadFaceLibrary() throws Exception {
        login(RoleEnum.DIRECTOR);when(faces.getAllFaceFeatures()).thenReturn(List.of());
        mvc.perform(get("/api/face/all").header("Authorization","Bearer synthetic")).andExpect(status().isOk());verify(faces).getAllFaceFeatures();
    }
}
