package com.qy.citytechupgrade.audit;

import com.qy.citytechupgrade.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditController {
    private final AuditService auditService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SYS_ADMIN','APPROVER_ADMIN')")
    public ApiResponse<List<AuditLogVO>> latest() {
        return ApiResponse.success(auditService.latest());
    }
}
