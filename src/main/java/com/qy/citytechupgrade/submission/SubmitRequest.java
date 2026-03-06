package com.qy.citytechupgrade.submission;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SubmitRequest {
    @NotNull(message = "submissionId不能为空")
    private Long submissionId;
}
