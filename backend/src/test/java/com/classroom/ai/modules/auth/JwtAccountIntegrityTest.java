package com.classroom.ai.modules.auth;

import com.classroom.ai.modules.auth.context.AuthContext;
import com.classroom.ai.modules.auth.entity.*;
import com.classroom.ai.modules.auth.repository.UserAccountRepository;
import com.classroom.ai.modules.auth.security.*;
import com.classroom.ai.modules.auth.vo.UserVO;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAccountIntegrityTest {
    @AfterEach void clear() { AuthContext.clear(); SecurityContextHolder.clearContext(); }
    final JwtTokenProvider tokens=mock(JwtTokenProvider.class);
    final UserAccountRepository accounts=mock(UserAccountRepository.class);
    UserVO run(UserVO claims) throws Exception {
        when(tokens.validateToken("synthetic-token")).thenReturn(true);
        when(tokens.parseUserFromToken("synthetic-token")).thenReturn(claims);
        var request=new MockHttpServletRequest();request.addHeader("Authorization","Bearer synthetic-token");
        var identity=new AtomicReference<UserVO>();
        new JwtAuthenticationFilter(tokens,accounts).doFilter(request,new MockHttpServletResponse(),(req,res)->{
            identity.set(AuthContext.getCurrentUser());
            assertEquals(identity.get()!=null,SecurityContextHolder.getContext().getAuthentication()!=null);
        });
        assertNull(AuthContext.getCurrentUser());return identity.get();
    }
    @Test void deletedAccountDoesNotAuthenticate() throws Exception {
        assertNull(run(UserVO.builder().id(900L).username("deleted").role(RoleEnum.DIRECTOR).department("A").build()));
    }
    @Test void deletedIdCannotInheritRecreatedSameNameAccount() throws Exception {
        when(accounts.findByUsername("reused")).thenReturn(Optional.of(UserAccount.builder().id(901L).username("reused").role(RoleEnum.DIRECTOR).build()));
        assertNull(run(UserVO.builder().id(900L).username("reused").role(RoleEnum.TEACHER).build()));
        verify(accounts,never()).findByUsername(any());
    }
    @Test void currentAccountRoleAndDepartmentOverrideOldClaims() throws Exception {
        when(accounts.findById(900L)).thenReturn(Optional.of(UserAccount.builder().id(900L).username("current").role(RoleEnum.TEACHER).department("B").build()));
        var user=run(UserVO.builder().id(900L).username("current").role(RoleEnum.DIRECTOR).department("A").build());
        assertEquals(RoleEnum.TEACHER,user.getRole());assertEquals("B",user.getDepartment());
    }
    @Test void mismatchedUsernameIsRejected() throws Exception {
        when(accounts.findById(900L)).thenReturn(Optional.of(UserAccount.builder().id(900L).username("another").role(RoleEnum.DIRECTOR).build()));
        assertNull(run(UserVO.builder().id(900L).username("old-name").build()));
    }
    @Test void legacyTokenWithoutIdStillReloadsExistingAccount() throws Exception {
        when(accounts.findByUsername("legacy")).thenReturn(Optional.of(UserAccount.builder().id(900L).username("legacy").role(RoleEnum.TEACHER).build()));
        assertEquals(900L,run(UserVO.builder().username("legacy").build()).getId());
    }
}
