package com.qy.citytechupgrade.submission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubmissionDigitalInfoRepository extends JpaRepository<SubmissionDigitalInfo, Long> {
    Optional<SubmissionDigitalInfo> findBySubmissionId(Long submissionId);
}
