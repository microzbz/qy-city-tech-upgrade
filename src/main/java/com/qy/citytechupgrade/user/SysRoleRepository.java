package com.qy.citytechupgrade.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SysRoleRepository extends JpaRepository<SysRole, Long> {
    Optional<SysRole> findByRoleCode(String roleCode);

    List<SysRole> findByRoleCodeIn(Collection<String> roleCodes);
}
