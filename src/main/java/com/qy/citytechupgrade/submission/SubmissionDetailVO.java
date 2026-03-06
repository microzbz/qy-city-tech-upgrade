package com.qy.citytechupgrade.submission;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class SubmissionDetailVO {
    private Long submissionId;
    private String documentNo;
    private Long enterpriseId;
    private Integer reportYear;
    private String status;
    private Integer currentNodeSeq;
    private String currentNodeName;
    private String reviewActionLabel;
    private String reviewComment;
    private LocalDateTime reviewHandledAt;
    private LocalDateTime submittedAt;
    private LocalDateTime updatedAt;
    private SubmissionSaveRequest.BasicInfo basicInfo;
    private SubmissionSaveRequest.DeviceInfo deviceInfo;
    private SubmissionSaveRequest.DigitalInfo digitalInfo;
    private SubmissionSaveRequest.RdToolInfo rdToolInfo;
    private List<SubmissionAttachmentVO> attachments;
}
