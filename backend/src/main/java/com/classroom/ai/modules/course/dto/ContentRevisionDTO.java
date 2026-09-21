package com.classroom.ai.modules.course.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentRevisionDTO {
    /** 当前草稿身份，防止不同草稿恰好使用同一锁版本。 */
    private Long draftId;
    private Long courseId;
    private String description;
    private String assessmentMethod;
    private String objectives;
    /** 历史/通用版本字段 */
    private Integer version;
    /** 并发锁版本 (客户端提交比对) */
    private Integer lockVersion;
    /** 内容发布版本 */
    private Integer publishVersion;

    public Integer getEffectiveLockVersion() {
        return lockVersion != null ? lockVersion : version;
    }
}
