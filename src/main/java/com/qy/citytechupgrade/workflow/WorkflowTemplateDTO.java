package com.qy.citytechupgrade.workflow;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class WorkflowTemplateDTO {
    private Long id;
    private String businessType;
    private String templateName;
    private Boolean active;
    private Boolean approvalEnabled;
    private List<WorkflowTemplateNodeDTO> nodes;
}
