package com.classroom.ai.modules.auth;

import com.classroom.ai.common.ApiExceptionHandler;
import com.classroom.ai.modules.auth.controller.AuthController;
import com.classroom.ai.modules.auth.dto.LoginDTO;
import com.classroom.ai.modules.auth.dto.SupervisorRegisterDTO;
import com.classroom.ai.modules.auth.entity.RoleEnum;
import com.classroom.ai.modules.auth.entity.UserAccount;
import com.classroom.ai.modules.auth.repository.UserAccountRepository;
import com.classroom.ai.modules.auth.security.JwtTokenProvider;
import com.classroom.ai.modules.auth.vo.UserVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthController authController;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("登录测试：合法凭据登录成功并返回会话用户信息及 JWT Token")
    void testLogin_Success() throws Exception {
        UserAccount account = UserAccount.builder()
                .id(1L)
                .username("guojun")
                .password("123456")
                .realName("郭军")
                .role(RoleEnum.TEACHER)
                .teacherCode("T2024001")
                .department("软件工程教研室")
                .build();

        when(userAccountRepository.findByUsername(eq("guojun"))).thenReturn(Optional.of(account));
        when(jwtTokenProvider.generateToken(any(UserVO.class))).thenReturn("mock-jwt-token-12345");

        LoginDTO dto = LoginDTO.builder().username("guojun").password("123456").build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("guojun"))
                .andExpect(jsonPath("$.data.realName").value("郭军"))
                .andExpect(jsonPath("$.data.role").value("TEACHER"))
                .andExpect(jsonPath("$.data.token").value("mock-jwt-token-12345"));
    }

    @Test
    @DisplayName("登录测试：密码错误时返回 401 拦截")
    void testLogin_WrongPassword_Returns401() throws Exception {
        UserAccount account = UserAccount.builder()
                .id(1L)
                .username("guojun")
                .password("123456")
                .realName("郭军")
                .role(RoleEnum.TEACHER)
                .build();

        when(userAccountRepository.findByUsername(eq("guojun"))).thenReturn(Optional.of(account));

        LoginDTO dto = LoginDTO.builder().username("guojun").password("wrongpass").build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    @DisplayName("会话测试：未登录请求 /me 返回 401")
    void testGetMe_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("会话测试：携带有效 Bearer Token 请求 /me 返回登录用户信息")
    void testGetMe_WithValidBearerToken_ReturnsUser() throws Exception {
        UserVO vo = UserVO.builder()
                .id(1L)
                .username("guojun")
                .realName("郭军")
                .role(RoleEnum.TEACHER)
                .department("软件工程教研室")
                .build();

        when(jwtTokenProvider.validateToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.parseUserFromToken("valid-token")).thenReturn(vo);

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("guojun"))
                .andExpect(jsonPath("$.data.realName").value("郭军"));
    }

    @Test
    @DisplayName("督导注册测试：合法督导专家注册成功并返回 Token")
    void testRegisterSupervisor_Success() throws Exception {
        SupervisorRegisterDTO dto = SupervisorRegisterDTO.builder()
                .username("supervisor_new")
                .password("123456")
                .realName("新督导")
                .department("校教学质量督导团")
                .authorizedMajors("SE;CS")
                .build();

        UserAccount savedAccount = UserAccount.builder()
                .id(20L)
                .username("supervisor_new")
                .password("123456")
                .realName("新督导")
                .role(RoleEnum.SUPERVISOR)
                .department("校教学质量督导团")
                .authorizedMajors("SE;CS")
                .build();

        when(userAccountRepository.findByUsername("supervisor_new")).thenReturn(Optional.empty());
        when(userAccountRepository.save(any(UserAccount.class))).thenReturn(savedAccount);
        when(jwtTokenProvider.generateToken(any(UserVO.class))).thenReturn("mock-supervisor-token");

        mockMvc.perform(post("/api/v1/auth/register-supervisor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("supervisor_new"))
                .andExpect(jsonPath("$.data.role").value("SUPERVISOR"))
                .andExpect(jsonPath("$.data.token").value("mock-supervisor-token"));
    }
}
