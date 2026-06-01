package com.qy.citytechupgrade.approval;

import com.qy.citytechupgrade.common.exception.BizException;
import com.qy.citytechupgrade.common.security.CurrentUser;
import com.qy.citytechupgrade.enterprise.EnterpriseProfile;
import com.qy.citytechupgrade.enterprise.EnterpriseProfileRepository;
import com.qy.citytechupgrade.enterprise.SurveyEnterprise;
import com.qy.citytechupgrade.enterprise.SurveyEnterpriseRepository;
import com.qy.citytechupgrade.submission.SubmissionBasicInfoRepository;
import com.qy.citytechupgrade.submission.SubmissionForm;
import com.qy.citytechupgrade.submission.SubmissionFormRepository;
import com.qy.citytechupgrade.user.SysUser;
import com.qy.citytechupgrade.user.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ApprovalDataScopeService {
    private static final String APPROVER_ROLE = "APPROVER_ADMIN";
    private static final String TOWN_MONITOR_ROLE = "TOWN_MONITOR";
    private static final String SYS_ADMIN_ROLE = "SYS_ADMIN";

    private final SysUserRepository sysUserRepository;
    private final SubmissionFormRepository submissionFormRepository;
    private final SubmissionBasicInfoRepository submissionBasicInfoRepository;
    private final EnterpriseProfileRepository enterpriseProfileRepository;
    private final SurveyEnterpriseRepository surveyEnterpriseRepository;

    public boolean canAccessSubmission(Long submissionId, CurrentUser currentUser) {
        if (submissionId == null) {
            return false;
        }
        SubmissionForm form = submissionFormRepository.findById(submissionId).orElse(null);
        return form != null && canAccessSubmission(form, currentUser);
    }

    public boolean canAccessSubmission(SubmissionForm form, CurrentUser currentUser) {
        if (form == null || currentUser == null || currentUser.getRoles() == null) {
            return false;
        }
        if (currentUser.getRoles().contains(SYS_ADMIN_ROLE)) {
            return true;
        }
        if (currentUser.getRoles().contains(APPROVER_ROLE) && canAccessByEnterpriseFirstDigit(form, currentUser)) {
            return true;
        }
        if (currentUser.getRoles().contains(TOWN_MONITOR_ROLE) && canAccessByTownStreetCode(form, currentUser)) {
            return true;
        }
        return false;
    }

    private boolean canAccessByEnterpriseFirstDigit(SubmissionForm form, CurrentUser currentUser) {
        String scope = currentUserScope(currentUser.getUserId());
        if (!StringUtils.hasText(scope)) {
            return true;
        }
        String firstDigit = resolveEnterpriseCodeFirstDigit(form);
        return StringUtils.hasText(firstDigit) && Objects.equals(scope, firstDigit.trim());
    }

    private boolean canAccessByTownStreetCode(SubmissionForm form, CurrentUser currentUser) {
        String scope = currentUserTownStreetScope(currentUser.getUserId());
        if (!StringUtils.hasText(scope)) {
            return false;
        }
        String townStreetCode = resolveTownStreetCode(form);
        return StringUtils.hasText(townStreetCode) && Objects.equals(scope, townStreetCode.trim());
    }

    public void assertCanAccessSubmission(Long submissionId, CurrentUser currentUser) {
        if (!canAccessSubmission(submissionId, currentUser)) {
            throw new BizException("无权访问该审批数据");
        }
    }

    public void assertCanAccessSubmission(SubmissionForm form, CurrentUser currentUser) {
        if (!canAccessSubmission(form, currentUser)) {
            throw new BizException("无权访问该审批数据");
        }
    }

    public boolean canReceiveApprovalNotice(SysUser user, String roleCode, Long submissionId) {
        if (user == null || !APPROVER_ROLE.equals(roleCode)) {
            return true;
        }
        String scope = normalizeScope(user.getEnterpriseCodeFirstDigitScope());
        if (!StringUtils.hasText(scope)) {
            return true;
        }
        SubmissionForm form = submissionId == null ? null : submissionFormRepository.findById(submissionId).orElse(null);
        String firstDigit = resolveEnterpriseCodeFirstDigit(form);
        return StringUtils.hasText(firstDigit) && Objects.equals(scope, firstDigit.trim());
    }

    public String resolveEnterpriseCodeFirstDigit(SubmissionForm form) {
        return resolveSurveyEnterprise(form)
            .map(item -> normalizeScope(item.getEnterpriseCodeFirstDigit()))
            .orElse(null);
    }

    public String resolveTownStreetCode(SubmissionForm form) {
        return resolveSurveyEnterprise(form)
            .map(item -> normalizeScope(item.getTownStreetCode()))
            .orElse(null);
    }

    private Optional<SurveyEnterprise> resolveSurveyEnterprise(SubmissionForm form) {
        if (form == null) {
            return Optional.empty();
        }
        String enterpriseName = enterpriseProfileRepository.findById(form.getEnterpriseId())
            .map(EnterpriseProfile::getEnterpriseName)
            .orElse(null);
        Optional<SurveyEnterprise> item = findByEnterpriseName(enterpriseName);
        if (item.isPresent()) {
            return item;
        }
        enterpriseName = submissionBasicInfoRepository.findBySubmissionId(form.getId())
            .map(info -> info.getEnterpriseName())
            .orElse(null);
        return findByEnterpriseName(enterpriseName);
    }

    private String currentUserScope(Long userId) {
        if (userId == null) {
            return null;
        }
        return sysUserRepository.findById(userId)
            .map(SysUser::getEnterpriseCodeFirstDigitScope)
            .map(this::normalizeScope)
            .orElse(null);
    }

    private String currentUserTownStreetScope(Long userId) {
        if (userId == null) {
            return null;
        }
        return sysUserRepository.findById(userId)
            .map(SysUser::getTownStreetCodeScope)
            .map(this::normalizeScope)
            .orElse(null);
    }

    private Optional<SurveyEnterprise> findByEnterpriseName(String enterpriseName) {
        if (!StringUtils.hasText(enterpriseName)) {
            return Optional.empty();
        }
        return surveyEnterpriseRepository.findFirstByEnterpriseNameOrderByIdAsc(enterpriseName.trim());
    }

    private String normalizeScope(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
