package com.qy.citytechupgrade.submission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubmissionAttachmentRepository extends JpaRepository<SubmissionAttachment, Long> {
    List<SubmissionAttachment> findBySubmissionIdOrderByUploadedAtDesc(Long submissionId);

    List<SubmissionAttachment> findBySubmissionIdAndAttachmentTypeIn(Long submissionId, List<String> attachmentTypes);

    long countBySubmissionIdAndAttachmentTypeIn(Long submissionId, List<String> attachmentTypes);

    long countBySubmissionIdAndAttachmentType(Long submissionId, String attachmentType);
}
