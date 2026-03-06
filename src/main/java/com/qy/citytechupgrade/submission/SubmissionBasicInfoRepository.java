package com.qy.citytechupgrade.submission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubmissionBasicInfoRepository extends JpaRepository<SubmissionBasicInfo, Long> {
    Optional<SubmissionBasicInfo> findBySubmissionId(Long submissionId);
}
