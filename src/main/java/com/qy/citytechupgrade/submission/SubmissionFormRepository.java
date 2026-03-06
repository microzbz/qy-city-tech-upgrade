package com.qy.citytechupgrade.submission;

import com.qy.citytechupgrade.common.enums.SubmissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubmissionFormRepository extends JpaRepository<SubmissionForm, Long> {
    Optional<SubmissionForm> findTopByEnterpriseIdAndStatusInOrderByUpdatedAtDesc(Long enterpriseId, List<SubmissionStatus> statuses);

    List<SubmissionForm> findByEnterpriseIdOrderByCreatedAtDesc(Long enterpriseId);

    List<SubmissionForm> findByStatusInOrderByUpdatedAtDesc(List<SubmissionStatus> statuses);

    boolean existsByDocumentNo(String documentNo);
}
