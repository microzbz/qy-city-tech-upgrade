package com.qy.citytechupgrade.audit;

import com.qy.citytechupgrade.common.dto.ApiResponse;
import com.qy.citytechupgrade.common.dto.PagedResult;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditController {
    private final AuditService auditService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SYS_ADMIN','APPROVER_ADMIN')")
    public ApiResponse<PagedResult<AuditLogVO>> latest(
        @RequestParam(required = false) String documentNo,
        @RequestParam(required = false) String companyName,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "20") Integer size
    ) {
        return ApiResponse.success(auditService.latest(documentNo, companyName, startTime, endTime, page, size));
    }
}
