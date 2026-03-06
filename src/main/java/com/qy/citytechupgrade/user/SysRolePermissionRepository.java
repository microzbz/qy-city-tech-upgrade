package com.qy.citytechupgrade.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SysRolePermissionRepository extends JpaRepository<SysRolePermission, Long> {
    List<SysRolePermission> findByRoleIdIn(List<Long> roleIds);
}
