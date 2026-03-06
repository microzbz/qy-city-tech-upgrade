package com.qy.citytechupgrade.submission.option;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface RdToolOptionRepository extends JpaRepository<RdToolOption, Long>, JpaSpecificationExecutor<RdToolOption> {
    List<RdToolOption> findByEnabledTrueOrderBySortNoAscIdAsc();

    Optional<RdToolOption> findFirstByEnabledTrueAndOtherOptionTrueOrderBySortNoAscIdAsc();

    Optional<RdToolOption> findFirstByOtherOptionTrue();
}
