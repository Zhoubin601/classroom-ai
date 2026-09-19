package com.classroom.ai.modules.auth.vo;

import com.classroom.ai.modules.auth.entity.RoleEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserVO implements Serializable {
    private Long id;
    private String username;
    private String realName;
    private RoleEnum role;
    private String department;
    private String teacherCode;
    private String authorizedMajors;
    /** JWT 访问凭证 */
    private String token;
}
