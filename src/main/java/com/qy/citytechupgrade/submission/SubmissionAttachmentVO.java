package com.qy.citytechupgrade.submission;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SubmissionAttachmentVO {
    private Long id;
    private String attachmentType;
    private String originalFileName;
    private String contentType;
    private LocalDateTime uploadedAt;
}
