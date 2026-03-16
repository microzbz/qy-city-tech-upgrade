package com.qy.citytechupgrade.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface SysNoticeRepository extends JpaRepository<SysNotice, Long> {
    List<SysNotice> findByUserIdOrderByCreatedAtDesc(Long userId);

    Page<SysNotice> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
