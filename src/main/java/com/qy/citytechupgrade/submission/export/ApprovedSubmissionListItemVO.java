package com.qy.citytechupgrade.submission.export;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ApprovedSubmissionListItemVO {
    private Long submissionId;
    private String documentNo;
    private Integer reportYear;
    private String enterpriseName;
    private String status;
    private String statusLabel;
    private String reviewActionLabel;
    private String reviewComment;
    private boolean exportable;
    private String exportHint;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewHandledAt;
}
