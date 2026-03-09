package com.qy.citytechupgrade.industry;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ProcessEquipmentMapRepository extends JpaRepository<ProcessEquipmentMap, Long>,
    JpaSpecificationExecutor<ProcessEquipmentMap> {
    List<ProcessEquipmentMap> findAllByIndustryCodeOrderByIdAsc(String industryCode);

    List<ProcessEquipmentMap> findAllByProcessNameAndIndustryCodeIsNullOrderByIdAsc(String processName);

    List<ProcessEquipmentMap> findAllByProcessNameAndIndustryCodeOrderByIdAsc(String processName, String industryCode);

    List<ProcessEquipmentMap> findAllByProcessNameOrderByIdAsc(String processName);

    boolean existsByProcessNameAndIndustryCodeIsNull(String processName);

    boolean existsByProcessNameAndIndustryCodeIsNullAndIdNot(String processName, Long id);

    boolean existsByProcessNameAndIndustryCode(String processName, String industryCode);

    boolean existsByProcessNameAndIndustryCodeAndIdNot(String processName, String industryCode, Long id);
}
