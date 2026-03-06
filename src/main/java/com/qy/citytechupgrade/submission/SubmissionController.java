package com.qy.citytechupgrade.submission;

import com.qy.citytechupgrade.approval.ApprovalService;
import com.qy.citytechupgrade.common.dto.ApiResponse;
import com.qy.citytechupgrade.common.security.CurrentUser;
import com.qy.citytechupgrade.common.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionController {
    private final SubmissionService submissionService;
    private final ApprovalService approvalService;

    @GetMapping("/current")
    @PreAuthorize("hasRole('ENTERPRISE_USER')")
    public ApiResponse<SubmissionDetailVO> current() {
        CurrentUser user = SecurityUtils.currentUser();
        return ApiResponse.success(submissionService.current(user));
    }

    @PostMapping("/save")
    @PreAuthorize("hasRole('ENTERPRISE_USER')")
    public ApiResponse<SubmissionDetailVO> save(@RequestBody SubmissionSaveRequest request) {
        CurrentUser user = SecurityUtils.currentUser();
        return ApiResponse.success(submissionService.saveDraft(request, user));
    }

    @PostMapping("/submit")
    @PreAuthorize("hasRole('ENTERPRISE_USER')")
    public ApiResponse<SubmissionDetailVO> submit(@RequestBody @Valid SubmitRequest request) {
        CurrentUser user = SecurityUtils.currentUser();
        submissionService.submit(request.getSubmissionId(), user);
        approvalService.startWorkflowForSubmission(request.getSubmissionId(), user);
        return ApiResponse.success(submissionService.getById(request.getSubmissionId(), user));
    }

    @GetMapping("/{id}")
    public ApiResponse<SubmissionDetailVO> detail(@PathVariable Long id) {
        return ApiResponse.success(submissionService.getById(id, SecurityUtils.currentUser()));
    }

    @GetMapping("/my-list")
    @PreAuthorize("hasRole('ENTERPRISE_USER')")
    public ApiResponse<List<SubmissionListItemVO>> myList() {
        return ApiResponse.success(submissionService.myList(SecurityUtils.currentUser()));
    }
}
