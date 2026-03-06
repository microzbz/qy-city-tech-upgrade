package com.qy.citytechupgrade.submission.option;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface DigitalSystemOptionRepository extends JpaRepository<DigitalSystemOption, Long>, JpaSpecificationExecutor<DigitalSystemOption> {
    List<DigitalSystemOption> findByEnabledTrueOrderBySortNoAscIdAsc();

    Optional<DigitalSystemOption> findFirstByEnabledTrueAndOtherOptionTrueOrderBySortNoAscIdAsc();

    Optional<DigitalSystemOption> findFirstByOtherOptionTrue();
}
