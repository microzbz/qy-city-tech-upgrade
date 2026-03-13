package com.qy.citytechupgrade.submission.export;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ApprovedSubmissionExportItemResultVO {
    private Long submissionId;
    private String enterpriseName;
    private String exportFolderName;
    private boolean success;
    private String message;
}
