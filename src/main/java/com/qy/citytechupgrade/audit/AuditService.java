package com.qy.citytechupgrade.audit;

import com.qy.citytechupgrade.user.SysUser;
import com.qy.citytechupgrade.user.SysRole;
import com.qy.citytechupgrade.user.SysRoleRepository;
import com.qy.citytechupgrade.user.SysUserRepository;
import com.qy.citytechupgrade.user.SysUserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditService {
    private static final String HIDDEN_ROLE_CODE = "SYS_ADMIN";

    private final AuditLogRepository auditLogRepository;
    private final SysUserRepository sysUserRepository;
    private final SysRoleRepository sysRoleRepository;
    private final SysUserRoleRepository sysUserRoleRepository;

    public void log(Long userId, String module, String action, String businessId, String detail) {
        AuditLog audit = new AuditLog();
        audit.setUserId(userId);
        audit.setModuleName(module);
        audit.setActionName(action);
        audit.setBusinessId(businessId);
        audit.setDetailText(detail);
        auditLogRepository.save(audit);
    }

    public List<AuditLogVO> latest() {
        Set<Long> hiddenUserIds = hiddenRoleUserIds();
        List<AuditLog> logs = auditLogRepository.findTop200ByOrderByCreatedAtDesc().stream()
            .filter(log -> log.getUserId() == null || !hiddenUserIds.contains(log.getUserId()))
            .toList();
        Map<Long, String> userNameMap = sysUserRepository.findAllById(
                logs.stream().map(AuditLog::getUserId).filter(Objects::nonNull).distinct().toList()
            ).stream()
            .collect(Collectors.toMap(SysUser::getId, SysUser::getDisplayName, (a, b) -> a));

        return logs.stream().map(log -> AuditLogVO.builder()
            .id(log.getId())
            .userId(log.getUserId())
            .userDisplayName(userNameMap.getOrDefault(log.getUserId(), "-"))
            .moduleName(log.getModuleName())
            .actionName(log.getActionName())
            .businessId(log.getBusinessId())
            .detailText(log.getDetailText())
            .createdAt(log.getCreatedAt())
            .build()
        ).toList();
    }

    private Set<Long> hiddenRoleUserIds() {
        SysRole hiddenRole = sysRoleRepository.findByRoleCode(HIDDEN_ROLE_CODE).orElse(null);
        if (hiddenRole == null) {
            return Set.of();
        }
        return new HashSet<>(sysUserRoleRepository.findByRoleId(hiddenRole.getId()).stream()
            .map(ur -> ur.getUserId())
            .filter(Objects::nonNull)
            .toList());
    }
}
