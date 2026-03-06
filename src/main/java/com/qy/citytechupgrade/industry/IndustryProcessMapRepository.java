package com.qy.citytechupgrade.industry;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface IndustryProcessMapRepository extends JpaRepository<IndustryProcessMap, Long>,
    JpaSpecificationExecutor<IndustryProcessMap> {
    Optional<IndustryProcessMap> findByIndustryCode(String industryCode);
}
