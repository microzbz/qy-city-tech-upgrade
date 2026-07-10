package com.qy.citytechupgrade.enterprise;

import com.qy.citytechupgrade.common.dto.ApiResponse;
import com.qy.citytechupgrade.common.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/enterprise")
@RequiredArgsConstructor
public class EnterpriseController {
    private final EnterpriseService enterpriseService;

    @GetMapping("/profile/by-credit-code/{creditCode}")
    public ApiResponse<EnterpriseProfileVO> getByCreditCode(@PathVariable String creditCode) {
        return ApiResponse.success(enterpriseService.getByCreditCode(creditCode));
    }

    @GetMapping("/profile/current")
    @PreAuthorize("hasRole('ENTERPRISE_USER')")
    public ApiResponse<EnterpriseProfileVO> getCurrentProfile() {
        return ApiResponse.success(enterpriseService.getCurrentEnterpriseProfile(SecurityUtils.currentUser()));
    }

    @PutMapping("/profile/current/contact")
    @PreAuthorize("hasRole('ENTERPRISE_USER')")
    public ApiResponse<EnterpriseProfileVO> updateCurrentContact(@RequestBody @Valid EnterpriseContactUpdateRequest request) {
        return ApiResponse.success("保存成功", enterpriseService.updateCurrentEnterpriseContact(SecurityUtils.currentUser(), request));
    }

    @GetMapping("/industry-code")
    public ApiResponse<String> getIndustryCodeByEnterpriseName(@RequestParam String enterpriseName) {
        return ApiResponse.success(enterpriseService.findIndustryCodeByEnterpriseName(enterpriseName));
    }

    @GetMapping("/industry-info")
    public ApiResponse<EnterpriseService.SurveyIndustryInfo> getIndustryInfoByEnterpriseName(@RequestParam String enterpriseName) {
        return ApiResponse.success(enterpriseService.findSurveyIndustryInfo(enterpriseName));
    }

    @GetMapping("/industry-name")
    public ApiResponse<String> getIndustryNameByIndustryCode(@RequestParam String industryCode) {
        return ApiResponse.success(enterpriseService.findIndustryNameByIndustryCode(industryCode));
    }
}
