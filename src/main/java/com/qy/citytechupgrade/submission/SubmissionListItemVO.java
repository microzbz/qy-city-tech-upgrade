package com.qy.citytechupgrade.submission;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SubmissionListItemVO {
    private Long submissionId;
    private String documentNo;
    private Integer reportYear;
    private String status;
    private String statusLabel;
    private String currentNodeName;
    private String progressText;
    private String reviewActionLabel;
    private String reviewComment;
    private LocalDateTime submittedAt;
    private LocalDateTime updatedAt;
}
