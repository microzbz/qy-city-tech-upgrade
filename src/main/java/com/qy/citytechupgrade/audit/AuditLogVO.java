package com.qy.citytechupgrade.audit;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AuditLogVO {
    private Long id;
    private Long userId;
    private String userDisplayName;
    private String moduleName;
    private String actionName;
    private String businessId;
    private String detailText;
    private LocalDateTime createdAt;
}
