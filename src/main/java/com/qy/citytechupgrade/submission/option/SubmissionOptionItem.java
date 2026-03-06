package com.qy.citytechupgrade.submission.option;

public record SubmissionOptionItem(
    Long id,
    String optionName,
    Integer sortNo,
    Boolean otherOption
) {
}
