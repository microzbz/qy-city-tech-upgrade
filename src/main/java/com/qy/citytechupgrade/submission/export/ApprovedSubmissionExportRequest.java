package com.qy.citytechupgrade.submission.export;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class ApprovedSubmissionExportRequest {
    @NotEmpty(message = "请选择至少一条已审批通过记录")
    private List<Long> submissionIds;
}
