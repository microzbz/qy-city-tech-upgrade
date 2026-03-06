package com.qy.citytechupgrade.workflow;

import lombok.Data;

@Data
public class WorkflowTemplateNodeDTO {
    private Integer nodeSeq;
    private String nodeName;
    private String roleCode;
    private Boolean allowApprove;
    private Boolean allowReject;
    private Boolean allowReturn;
}
