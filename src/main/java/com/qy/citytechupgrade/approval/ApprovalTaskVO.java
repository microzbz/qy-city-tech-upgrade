package com.qy.citytechupgrade.approval;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ApprovalTaskVO {
    private Long taskId;
    private Long submissionId;
    private String documentNo;
    private Integer reportYear;
    private String submissionStatus;
    private String enterpriseName;
    private String nodeName;
    private String roleCode;
    private String taskStatus;
    private String action;
    private String comment;
    private LocalDateTime createdAt;
    private LocalDateTime handledAt;
}
