package com.qy.citytechupgrade.approval;

import com.qy.citytechupgrade.common.dto.ApiResponse;
import com.qy.citytechupgrade.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/approvals")
@RequiredArgsConstructor
public class ApprovalController {
    private final ApprovalService approvalService;

    @GetMapping("/todo")
    @PreAuthorize("hasAnyRole('APPROVER_ADMIN','SYS_ADMIN')")
    public ApiResponse<List<ApprovalTaskVO>> todo() {
        return ApiResponse.success(approvalService.todo(SecurityUtils.currentUser()));
    }

    @GetMapping("/done")
    @PreAuthorize("hasAnyRole('APPROVER_ADMIN','SYS_ADMIN')")
    public ApiResponse<List<ApprovalTaskVO>> done() {
        return ApiResponse.success(approvalService.done(SecurityUtils.currentUser()));
    }

    @GetMapping("/{taskId}")
    @PreAuthorize("hasAnyRole('APPROVER_ADMIN','SYS_ADMIN')")
    public ApiResponse<ApprovalTaskVO> detail(@PathVariable Long taskId) {
        return ApiResponse.success(approvalService.detail(taskId, SecurityUtils.currentUser()));
    }

    @PostMapping("/{taskId}/approve")
    @PreAuthorize("hasAnyRole('APPROVER_ADMIN','SYS_ADMIN')")
    public ApiResponse<String> approve(@PathVariable Long taskId, @RequestBody(required = false) ApprovalActionRequest req) {
        approvalService.approve(taskId, req == null ? null : req.getComment(), SecurityUtils.currentUser());
        return ApiResponse.success("审批通过", "OK");
    }

    @PostMapping("/{taskId}/reject")
    @PreAuthorize("hasAnyRole('APPROVER_ADMIN','SYS_ADMIN')")
    public ApiResponse<String> reject(@PathVariable Long taskId, @RequestBody(required = false) ApprovalActionRequest req) {
        approvalService.reject(taskId, req == null ? null : req.getComment(), SecurityUtils.currentUser());
        return ApiResponse.success("已驳回", "OK");
    }

    @PostMapping("/{taskId}/return")
    @PreAuthorize("hasAnyRole('APPROVER_ADMIN','SYS_ADMIN')")
    public ApiResponse<String> returnBack(@PathVariable Long taskId, @RequestBody(required = false) ApprovalActionRequest req) {
        approvalService.returnBack(taskId, req == null ? null : req.getComment(), SecurityUtils.currentUser());
        return ApiResponse.success("已退回", "OK");
    }
}
