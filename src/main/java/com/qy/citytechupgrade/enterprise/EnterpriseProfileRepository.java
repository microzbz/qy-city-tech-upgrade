package com.qy.citytechupgrade.enterprise;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EnterpriseProfileRepository extends JpaRepository<EnterpriseProfile, Long> {
    Optional<EnterpriseProfile> findByCreditCode(String creditCode);

    List<EnterpriseProfile> findByEnterpriseNameContainingIgnoreCase(String enterpriseName);
}
