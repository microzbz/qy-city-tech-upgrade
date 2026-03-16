package com.qy.citytechupgrade.enterprise;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SurveyEnterpriseUpsertRequest {
    @NotBlank(message = "所属镇街园区不能为空")
    @Size(max = 100, message = "所属镇街园区长度不能超过100")
    private String townPark;

    @NotBlank(message = "企业名称不能为空")
    @Size(max = 255, message = "企业名称长度不能超过255")
    private String enterpriseName;

    @Size(max = 20, message = "所属行业代码长度不能超过20")
    private String industryCode;

    @Size(max = 1, message = "企业编码第1位长度不能超过1")
    private String enterpriseCodeFirstDigit;

    @Size(max = 2, message = "企业编码第2-3位长度不能超过2")
    private String enterpriseCodeTownDigits;

    @Size(max = 2, message = "企业编码第4-5位长度不能超过2")
    private String enterpriseCodeIndustryDigits;

    @Size(max = 3, message = "企业编码第6-8位长度不能超过3")
    private String enterpriseCodeSequenceDigits;

    private Integer sourceRowNo;
}
