package com.qy.citytechupgrade.enterprise;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SurveyEnterpriseRepository extends JpaRepository<SurveyEnterprise, Long>, JpaSpecificationExecutor<SurveyEnterprise> {
}
