package com.qy.citytechupgrade.approval;

import com.qy.citytechupgrade.common.dto.ApiResponse;
import com.qy.citytechupgrade.common.dto.PagedResult;
import com.qy.citytechupgrade.common.security.SecurityUtils;
import com.qy.citytechupgrade.submission.SubmissionDetailVO;
import com.qy.citytechupgrade.submission.SubmissionSaveRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/approvals")
@RequiredArgsConstructor
public class ApprovalController {
    private final ApprovalService approvalService;

    @GetMapping("/todo")
    @PreAuthorize("hasAnyRole('APPROVER_ADMIN','SYS_ADMIN')")
    public ApiResponse<PagedResult<ApprovalTaskVO>> todo(
        @RequestParam(required = false) String documentNo,
        @RequestParam(required = false) String enterpriseName,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "20") Integer size
    ) {
        return ApiResponse.success(approvalService.todo(
            documentNo, enterpriseName, startTime, endTime, page, size, SecurityUtils.currentUser()
        ));
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

    @PostMapping("/submissions/{submissionId}/save-edit")
    @PreAuthorize("hasAnyRole('APPROVER_ADMIN','SYS_ADMIN')")
    public ApiResponse<SubmissionDetailVO> saveEdit(@PathVariable Long submissionId, @RequestBody SubmissionSaveRequest request) {
        return ApiResponse.success(approvalService.saveEditedSubmission(submissionId, request, SecurityUtils.currentUser()));
    }

    @PostMapping("/submissions/{submissionId}/submit-edit")
    @PreAuthorize("hasAnyRole('APPROVER_ADMIN','SYS_ADMIN')")
    public ApiResponse<SubmissionDetailVO> submitEdit(@PathVariable Long submissionId) {
        return ApiResponse.success(approvalService.submitEditedSubmission(submissionId, SecurityUtils.currentUser()));
    }

    @PostMapping("/submissions/{submissionId}/return-approved")
    @PreAuthorize("hasAnyRole('APPROVER_ADMIN','SYS_ADMIN')")
    public ApiResponse<SubmissionDetailVO> returnApproved(@PathVariable Long submissionId,
                                                          @RequestBody(required = false) ApprovalActionRequest req) {
        return ApiResponse.success(approvalService.returnApprovedSubmission(
            submissionId,
            req == null ? null : req.getComment(),
            SecurityUtils.currentUser()
        ));
    }
}
