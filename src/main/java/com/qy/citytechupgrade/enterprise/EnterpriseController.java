package com.qy.citytechupgrade.enterprise;

import com.qy.citytechupgrade.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping("/industry-code")
    public ApiResponse<String> getIndustryCodeByEnterpriseName(@RequestParam String enterpriseName) {
        return ApiResponse.success(enterpriseService.findIndustryCodeByEnterpriseName(enterpriseName));
    }
}
