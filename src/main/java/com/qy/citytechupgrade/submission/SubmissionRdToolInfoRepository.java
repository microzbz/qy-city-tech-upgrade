package com.qy.citytechupgrade.submission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubmissionRdToolInfoRepository extends JpaRepository<SubmissionRdToolInfo, Long> {
    Optional<SubmissionRdToolInfo> findBySubmissionId(Long submissionId);
}
