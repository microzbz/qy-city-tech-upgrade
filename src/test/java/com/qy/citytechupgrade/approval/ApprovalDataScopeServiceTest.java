package com.qy.citytechupgrade.approval;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalDataScopeServiceTest {
    @Mock
    private SysUserRepository sysUserRepository;

    @Mock
    private SubmissionFormRepository submissionFormRepository;

    @Mock
    private SubmissionBasicInfoRepository submissionBasicInfoRepository;

    @Mock
    private EnterpriseProfileRepository enterpriseProfileRepository;

    @Mock
    private SurveyEnterpriseRepository surveyEnterpriseRepository;

    @InjectMocks
    private ApprovalDataScopeService approvalDataScopeService;

    @Test
    void scopedApproverCanAccessMatchingFirstDigit() {
        CurrentUser currentUser = currentUser();
        SubmissionForm form = submissionForm(10L, 20L);
        mockScopeUser("1");
        mockEnterpriseFirstDigit(20L, "企业A", "1");

        assertThat(approvalDataScopeService.canAccessSubmission(form, currentUser)).isTrue();
    }

    @Test
    void scopedApproverCannotAccessDifferentFirstDigit() {
        CurrentUser currentUser = currentUser();
        SubmissionForm form = submissionForm(10L, 20L);
        mockScopeUser("1");
        mockEnterpriseFirstDigit(20L, "企业B", "2");

        assertThat(approvalDataScopeService.canAccessSubmission(form, currentUser)).isFalse();
    }

    @Test
    void unrestrictedApproverCanAccessUnknownFirstDigit() {
        CurrentUser currentUser = currentUser();
        SubmissionForm form = submissionForm(10L, 20L);
        mockScopeUser(null);

        assertThat(approvalDataScopeService.canAccessSubmission(form, currentUser)).isTrue();
    }

    @Test
    void scopedApproverCannotAccessUnknownFirstDigit() {
        CurrentUser currentUser = currentUser();
        SubmissionForm form = submissionForm(10L, 20L);
        mockScopeUser("1");
        EnterpriseProfile profile = new EnterpriseProfile();
        profile.setId(20L);
        profile.setEnterpriseName("企业C");
        when(enterpriseProfileRepository.findById(20L)).thenReturn(Optional.of(profile));
        when(surveyEnterpriseRepository.findFirstByEnterpriseNameOrderByIdAsc("企业C")).thenReturn(Optional.empty());
        when(submissionBasicInfoRepository.findBySubmissionId(10L)).thenReturn(Optional.empty());

        assertThat(approvalDataScopeService.canAccessSubmission(form, currentUser)).isFalse();
    }

    @Test
    void townMonitorCanAccessMatchingTownStreetCode() {
        CurrentUser currentUser = townMonitorUser();
        SubmissionForm form = submissionForm(10L, 20L);
        mockTownMonitorUser("18");
        mockEnterpriseTownStreetCode(20L, "企业D", "18");

        assertThat(approvalDataScopeService.canAccessSubmission(form, currentUser)).isTrue();
    }

    @Test
    void townMonitorCannotAccessDifferentTownStreetCode() {
        CurrentUser currentUser = townMonitorUser();
        SubmissionForm form = submissionForm(10L, 20L);
        mockTownMonitorUser("18");
        mockEnterpriseTownStreetCode(20L, "企业E", "19");

        assertThat(approvalDataScopeService.canAccessSubmission(form, currentUser)).isFalse();
    }

    private CurrentUser currentUser() {
        return new CurrentUser(1L, "approver", "审批员", null, Set.of("APPROVER_ADMIN"));
    }

    private CurrentUser townMonitorUser() {
        return new CurrentUser(2L, "cs_monitor", "茶山镇查阅账号", null, Set.of("TOWN_MONITOR"));
    }

    private void mockScopeUser(String scope) {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setEnterpriseCodeFirstDigitScope(scope);
        when(sysUserRepository.findById(1L)).thenReturn(Optional.of(user));
    }

    private void mockTownMonitorUser(String townStreetCode) {
        SysUser user = new SysUser();
        user.setId(2L);
        user.setTownStreetCodeScope(townStreetCode);
        when(sysUserRepository.findById(2L)).thenReturn(Optional.of(user));
    }

    private SubmissionForm submissionForm(Long id, Long enterpriseId) {
        SubmissionForm form = new SubmissionForm();
        form.setId(id);
        form.setEnterpriseId(enterpriseId);
        return form;
    }

    private void mockEnterpriseFirstDigit(Long enterpriseId, String enterpriseName, String firstDigit) {
        EnterpriseProfile profile = new EnterpriseProfile();
        profile.setId(enterpriseId);
        profile.setEnterpriseName(enterpriseName);
        when(enterpriseProfileRepository.findById(enterpriseId)).thenReturn(Optional.of(profile));

        SurveyEnterprise surveyEnterprise = new SurveyEnterprise();
        surveyEnterprise.setEnterpriseName(enterpriseName);
        surveyEnterprise.setEnterpriseCodeFirstDigit(firstDigit);
        when(surveyEnterpriseRepository.findFirstByEnterpriseNameOrderByIdAsc(enterpriseName))
            .thenReturn(Optional.of(surveyEnterprise));
    }

    private void mockEnterpriseTownStreetCode(Long enterpriseId, String enterpriseName, String townStreetCode) {
        EnterpriseProfile profile = new EnterpriseProfile();
        profile.setId(enterpriseId);
        profile.setEnterpriseName(enterpriseName);
        when(enterpriseProfileRepository.findById(enterpriseId)).thenReturn(Optional.of(profile));

        SurveyEnterprise surveyEnterprise = new SurveyEnterprise();
        surveyEnterprise.setEnterpriseName(enterpriseName);
        surveyEnterprise.setTownStreetCode(townStreetCode);
        when(surveyEnterpriseRepository.findFirstByEnterpriseNameOrderByIdAsc(enterpriseName))
            .thenReturn(Optional.of(surveyEnterprise));
    }
}
