package com.qy.citytechupgrade.submission;

import com.qy.citytechupgrade.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubmissionServiceTest {
    @Test
    void assertVersionMatchesRejectsStaleVersion() {
        SubmissionForm form = new SubmissionForm();
        form.setVersion(2L);

        assertThatThrownBy(() -> SubmissionService.assertVersionMatches(form, 1L))
            .isInstanceOf(BizException.class)
            .hasMessage(SubmissionService.STALE_DATA_MESSAGE);
    }
}
