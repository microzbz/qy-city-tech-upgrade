package com.qy.citytechupgrade.enterprise;

import com.qy.citytechupgrade.audit.AuditService;
import com.qy.citytechupgrade.common.dto.ApiResponse;
import com.qy.citytechupgrade.common.dto.PagedResult;
import com.qy.citytechupgrade.common.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/survey-enterprises")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SYS_ADMIN')")
public class SurveyEnterpriseController {
    private final SurveyEnterpriseService surveyEnterpriseService;
    private final AuditService auditService;

    @GetMapping
    public ApiResponse<PagedResult<SurveyEnterprise>> list(
        @RequestParam(required = false) String townPark,
        @RequestParam(required = false) String enterpriseName,
        @RequestParam(required = false) String industryCode,
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "20") Integer size
    ) {
        return ApiResponse.success(surveyEnterpriseService.list(townPark, enterpriseName, industryCode, page, size));
    }

    @PostMapping
    public ApiResponse<SurveyEnterprise> create(@RequestBody @Valid SurveyEnterpriseUpsertRequest request) {
        SurveyEnterprise created = surveyEnterpriseService.create(request);
        auditService.log(SecurityUtils.currentUserId(), "SURVEY_ENTERPRISE", "CREATE",
            String.valueOf(created.getId()), created.getEnterpriseName());
        return ApiResponse.success(created);
    }

    @PutMapping("/{id}")
    public ApiResponse<SurveyEnterprise> update(@PathVariable Long id, @RequestBody @Valid SurveyEnterpriseUpsertRequest request) {
        SurveyEnterprise updated = surveyEnterpriseService.update(id, request);
        auditService.log(SecurityUtils.currentUserId(), "SURVEY_ENTERPRISE", "UPDATE",
            String.valueOf(updated.getId()), updated.getEnterpriseName());
        return ApiResponse.success(updated);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<String> delete(@PathVariable Long id) {
        surveyEnterpriseService.delete(id);
        auditService.log(SecurityUtils.currentUserId(), "SURVEY_ENTERPRISE", "DELETE",
            String.valueOf(id), "删除调研企业信息");
        return ApiResponse.success("删除成功", "OK");
    }
}
