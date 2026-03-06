package com.qy.citytechupgrade.workflow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class WorkflowTemplateUpsertRequest {
    @NotBlank(message = "业务类型不能为空")
    private String businessType;

    @NotBlank(message = "模板名称不能为空")
    private String templateName;

    private Boolean active;
    private Boolean approvalEnabled;

    @NotEmpty(message = "节点不能为空")
    private List<WorkflowTemplateNodeDTO> nodes;
}
