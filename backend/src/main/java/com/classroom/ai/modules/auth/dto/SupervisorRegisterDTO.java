package com.classroom.ai.modules.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupervisorRegisterDTO {
    private String username;
    private String password;
    private String realName;
    private String department;
    private String authorizedMajors;
}
