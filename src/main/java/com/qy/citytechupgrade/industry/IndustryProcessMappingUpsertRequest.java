package com.qy.citytechupgrade.industry;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class IndustryProcessMappingUpsertRequest {
    @NotBlank(message = "行业代码不能为空")
    private String industryCode;

    @NotBlank(message = "行业名称不能为空")
    private String industryName;

    @NotBlank(message = "主要工序不能为空")
    private String processNamesText;
}
