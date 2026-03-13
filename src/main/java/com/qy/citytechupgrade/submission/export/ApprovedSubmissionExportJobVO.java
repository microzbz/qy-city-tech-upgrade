package com.qy.citytechupgrade.submission.export;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ApprovedSubmissionExportJobVO {
    private String jobId;
    private String status;
    private String message;
    private Integer totalCount;
    private Integer completedCount;
    private Integer successCount;
    private Integer failedCount;
    private Long currentSubmissionId;
    private String currentEnterpriseName;
    private String fileName;
    private boolean downloadReady;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private List<ApprovedSubmissionExportItemResultVO> items;
}
