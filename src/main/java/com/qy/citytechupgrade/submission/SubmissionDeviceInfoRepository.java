package com.qy.citytechupgrade.submission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubmissionDeviceInfoRepository extends JpaRepository<SubmissionDeviceInfo, Long> {
    Optional<SubmissionDeviceInfo> findBySubmissionId(Long submissionId);
}
