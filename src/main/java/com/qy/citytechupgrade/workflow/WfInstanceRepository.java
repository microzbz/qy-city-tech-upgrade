package com.qy.citytechupgrade.workflow;

import com.qy.citytechupgrade.common.enums.WorkflowStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WfInstanceRepository extends JpaRepository<WfInstance, Long> {
    Optional<WfInstance> findByBusinessTypeAndBusinessId(String businessType, Long businessId);

    boolean existsByTemplateIdAndStatus(Long templateId, WorkflowStatus status);

    boolean existsByTemplateId(Long templateId);
}
