package com.qy.citytechupgrade.enterprise;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EnterpriseContactUpdateRequest {
    @NotBlank(message = "推送消息联系人不能为空")
    @Size(max = 128, message = "推送消息联系人不能超过128个字符")
    private String contactName;

    @NotBlank(message = "联系人身份证号不能为空")
    @Size(max = 64, message = "联系人身份证号不能超过64个字符")
    private String contactCertNo;
}
