package com.qy.citytechupgrade.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SysNoticeRepository extends JpaRepository<SysNotice, Long> {
    List<SysNotice> findByUserIdOrderByCreatedAtDesc(Long userId);
}
