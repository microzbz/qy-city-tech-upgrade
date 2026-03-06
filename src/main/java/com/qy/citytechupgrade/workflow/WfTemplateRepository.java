package com.qy.citytechupgrade.workflow;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WfTemplateRepository extends JpaRepository<WfTemplate, Long> {
    Optional<WfTemplate> findFirstByBusinessTypeAndActiveTrueOrderByUpdatedAtDesc(String businessType);

    List<WfTemplate> findByBusinessTypeOrderByUpdatedAtDesc(String businessType);
}
