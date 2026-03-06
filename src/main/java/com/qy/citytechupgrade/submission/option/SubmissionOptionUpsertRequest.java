package com.qy.citytechupgrade.submission.option;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubmissionOptionUpsertRequest {
    @NotBlank(message = "选项名称不能为空")
    private String optionName;

    @NotNull(message = "序号不能为空")
    @Min(value = 1, message = "序号必须大于0")
    private Integer sortNo;

    private Boolean otherOption;

    private Boolean enabled;
}
