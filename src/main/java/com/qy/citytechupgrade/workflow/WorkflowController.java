package com.qy.citytechupgrade.workflow;

import com.qy.citytechupgrade.audit.AuditService;
import com.qy.citytechupgrade.common.dto.ApiResponse;
import com.qy.citytechupgrade.common.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workflow/templates")
@RequiredArgsConstructor
public class WorkflowController {
    private final WorkflowService workflowService;
    private final AuditService auditService;

    @GetMapping
    @PreAuthorize("hasRole('SYS_ADMIN')")
    public ApiResponse<List<WorkflowTemplateDTO>> list(@RequestParam(defaultValue = "SUBMISSION") String businessType) {
        return ApiResponse.success(workflowService.listByBusinessType(businessType));
    }

    @PostMapping
    @PreAuthorize("hasRole('SYS_ADMIN')")
    public ApiResponse<WorkflowTemplateDTO> create(@RequestBody @Valid WorkflowTemplateUpsertRequest request) {
        WorkflowTemplateDTO created = workflowService.create(request, SecurityUtils.currentUserId());
        auditService.log(SecurityUtils.currentUserId(), "WORKFLOW", "CREATE_TEMPLATE",
            String.valueOf(created.getId()), created.getTemplateName());
        return ApiResponse.success(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    public ApiResponse<WorkflowTemplateDTO> update(@PathVariable Long id,
                                                   @RequestBody @Valid WorkflowTemplateUpsertRequest request) {
        WorkflowTemplateDTO updated = workflowService.update(id, request);
        auditService.log(SecurityUtils.currentUserId(), "WORKFLOW", "UPDATE_TEMPLATE",
            String.valueOf(updated.getId()), updated.getTemplateName());
        return ApiResponse.success(updated);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    public ApiResponse<WorkflowTemplateDTO> activate(@PathVariable Long id) {
        WorkflowTemplateDTO activated = workflowService.activate(id);
        auditService.log(SecurityUtils.currentUserId(), "WORKFLOW", "ACTIVATE_TEMPLATE",
            String.valueOf(activated.getId()), activated.getTemplateName());
        return ApiResponse.success(activated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    public ApiResponse<String> delete(@PathVariable Long id) {
        workflowService.delete(id);
        auditService.log(SecurityUtils.currentUserId(), "WORKFLOW", "DELETE_TEMPLATE",
            String.valueOf(id), "删除流程模板");
        return ApiResponse.success("删除成功", "OK");
    }
}
