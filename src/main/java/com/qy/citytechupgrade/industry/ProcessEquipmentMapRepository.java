package com.qy.citytechupgrade.industry;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ProcessEquipmentMapRepository extends JpaRepository<ProcessEquipmentMap, Long>,
    JpaSpecificationExecutor<ProcessEquipmentMap> {
    Optional<ProcessEquipmentMap> findByProcessName(String processName);

    boolean existsByProcessName(String processName);

    boolean existsByProcessNameAndIdNot(String processName, Long id);
}
