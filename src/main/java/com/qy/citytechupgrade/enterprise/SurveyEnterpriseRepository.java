package com.qy.citytechupgrade.enterprise;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface SurveyEnterpriseRepository extends JpaRepository<SurveyEnterprise, Long>, JpaSpecificationExecutor<SurveyEnterprise> {
    Optional<SurveyEnterprise> findFirstByEnterpriseNameOrderByIdAsc(String enterpriseName);
}
