package com.qy.citytechupgrade.audit;

import com.qy.citytechupgrade.common.dto.PagedResult;
import com.qy.citytechupgrade.enterprise.EnterpriseProfile;
import com.qy.citytechupgrade.enterprise.EnterpriseProfileRepository;
import com.qy.citytechupgrade.submission.SubmissionForm;
import com.qy.citytechupgrade.submission.SubmissionFormRepository;
import com.qy.citytechupgrade.user.SysUser;
import com.qy.citytechupgrade.user.SysRole;
import com.qy.citytechupgrade.user.SysRoleRepository;
import com.qy.citytechupgrade.user.SysUserRepository;
import com.qy.citytechupgrade.user.SysUserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    private final SubmissionFormRepository submissionFormRepository;
    private final EnterpriseProfileRepository enterpriseProfileRepository;

    public void log(Long userId, String module, String action, String businessId, String detail) {
        AuditLog audit = new AuditLog();
        audit.setUserId(userId);
        audit.setModuleName(module);
        audit.setActionName(action);
        audit.setBusinessId(businessId);
        audit.setDetailText(detail);
        auditLogRepository.save(audit);
    }

    public PagedResult<AuditLogVO> latest(
        String documentNo,
        String companyName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer page,
        Integer size
    ) {
        String normalizedDocumentNo = normalizeKeyword(documentNo);
        String normalizedCompanyName = normalizeKeyword(companyName);
        Set<Long> hiddenUserIds = hiddenRoleUserIds();
        Pageable pageable = buildPageable(page, size);
        Page<AuditLog> result = auditLogRepository.findAll(
            buildSpecification(normalizedDocumentNo, normalizedCompanyName, startTime, endTime, hiddenUserIds),
            pageable
        );
        List<AuditLog> logs = result.getContent();
        Map<Long, SubmissionForm> submissionMap = findSubmissionMap(logs);
        Map<Long, EnterpriseProfile> enterpriseMap = findEnterpriseMap(submissionMap.values());
        Map<Long, String> userNameMap = sysUserRepository.findAllById(
                logs.stream().map(AuditLog::getUserId).filter(Objects::nonNull).distinct().toList()
            ).stream()
            .collect(Collectors.toMap(SysUser::getId, SysUser::getDisplayName, (a, b) -> a));

        List<AuditLogVO> records = logs.stream().map(log -> AuditLogVO.builder()
            .id(log.getId())
            .userId(log.getUserId())
            .userDisplayName(userNameMap.getOrDefault(log.getUserId(), "-"))
            .documentNo(resolveDocumentNo(log, submissionMap))
            .enterpriseName(resolveEnterpriseName(log, submissionMap, enterpriseMap))
            .moduleName(log.getModuleName())
            .actionName(log.getActionName())
            .businessId(log.getBusinessId())
            .detailText(log.getDetailText())
            .createdAt(log.getCreatedAt())
            .build()
        ).toList();
        return PagedResult.of(records, result.getTotalElements(), result.getNumber() + 1, result.getSize());
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

    private Specification<AuditLog> buildSpecification(
        String documentNo,
        String companyName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Set<Long> hiddenUserIds
    ) {
        Set<String> matchedBusinessIds = findMatchedBusinessIds(documentNo, companyName);
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            if (!hiddenUserIds.isEmpty()) {
                predicates.add(cb.or(
                    cb.isNull(root.get("userId")),
                    cb.not(root.get("userId").in(hiddenUserIds))
                ));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endTime));
            }
            if (matchedBusinessIds != null) {
                if (matchedBusinessIds.isEmpty()) {
                    return cb.disjunction();
                }
                predicates.add(root.get("businessId").in(matchedBusinessIds));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private Pageable buildPageable(Integer page, Integer size) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        return PageRequest.of(
            safePage - 1,
            safeSize,
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
    }

    private Set<String> findMatchedBusinessIds(String documentNo, String companyName) {
        if (documentNo == null && companyName == null) {
            return null;
        }
        Set<Long> enterpriseIds = null;
        if (companyName != null) {
            enterpriseIds = enterpriseProfileRepository.findByEnterpriseNameContainingIgnoreCase(companyName).stream()
                .map(EnterpriseProfile::getId)
                .collect(Collectors.toSet());
            if (enterpriseIds.isEmpty()) {
                return Set.of();
            }
        }
        Set<Long> finalEnterpriseIds = enterpriseIds;
        List<SubmissionForm> forms = submissionFormRepository.findAll((root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            if (documentNo != null) {
                predicates.add(cb.like(cb.lower(root.get("documentNo")), "%" + documentNo + "%"));
            }
            if (finalEnterpriseIds != null) {
                predicates.add(root.get("enterpriseId").in(finalEnterpriseIds));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        });
        return forms.stream()
            .map(SubmissionForm::getId)
            .filter(Objects::nonNull)
            .map(String::valueOf)
            .collect(Collectors.toSet());
    }

    private Map<Long, SubmissionForm> findSubmissionMap(List<AuditLog> logs) {
        List<Long> submissionIds = logs.stream()
            .map(AuditLog::getBusinessId)
            .map(this::parseLong)
            .flatMap(Optional::stream)
            .distinct()
            .toList();
        return submissionFormRepository.findAllById(submissionIds).stream()
            .collect(Collectors.toMap(SubmissionForm::getId, form -> form, (a, b) -> a));
    }

    private Map<Long, EnterpriseProfile> findEnterpriseMap(java.util.Collection<SubmissionForm> forms) {
        List<Long> enterpriseIds = forms.stream()
            .map(SubmissionForm::getEnterpriseId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        return enterpriseProfileRepository.findAllById(enterpriseIds).stream()
            .collect(Collectors.toMap(EnterpriseProfile::getId, enterprise -> enterprise, (a, b) -> a));
    }

    private String resolveDocumentNo(AuditLog log, Map<Long, SubmissionForm> submissionMap) {
        SubmissionForm form = parseLong(log.getBusinessId()).map(submissionMap::get).orElse(null);
        return form == null ? "-" : defaultText(form.getDocumentNo());
    }

    private String resolveEnterpriseName(
        AuditLog log,
        Map<Long, SubmissionForm> submissionMap,
        Map<Long, EnterpriseProfile> enterpriseMap
    ) {
        SubmissionForm form = parseLong(log.getBusinessId()).map(submissionMap::get).orElse(null);
        if (form == null) {
            return "-";
        }
        EnterpriseProfile enterprise = enterpriseMap.get(form.getEnterpriseId());
        return enterprise == null ? "-" : defaultText(enterprise.getEnterpriseName());
    }

    private Optional<Long> parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(value.trim()));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private String normalizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return keyword.trim().toLowerCase();
    }

    private String defaultText(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }
}
