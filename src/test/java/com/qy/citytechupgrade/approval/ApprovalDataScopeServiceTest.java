package com.qy.citytechupgrade.approval;

import com.qy.citytechupgrade.common.security.CurrentUser;
import com.qy.citytechupgrade.enterprise.EnterpriseProfile;
import com.qy.citytechupgrade.enterprise.EnterpriseProfileRepository;
import com.qy.citytechupgrade.enterprise.SurveyEnterprise;
import com.qy.citytechupgrade.enterprise.SurveyEnterpriseRepository;
import com.qy.citytechupgrade.submission.SubmissionBasicInfo;
import com.qy.citytechupgrade.submission.SubmissionBasicInfoRepository;
import com.qy.citytechupgrade.submission.SubmissionForm;
import com.qy.citytechupgrade.submission.SubmissionFormRepository;
import com.qy.citytechupgrade.user.SysUser;
import com.qy.citytechupgrade.user.SysUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalDataScopeServiceTest {
    private Map<Long, SysUser> users;
    private Map<Long, SubmissionForm> submissions;
    private Map<Long, SubmissionBasicInfo> basicInfos;
    private Map<Long, EnterpriseProfile> enterpriseProfiles;
    private Map<String, SurveyEnterprise> surveyEnterprises;
    private ApprovalDataScopeService approvalDataScopeService;

    @BeforeEach
    void setUp() {
        users = new HashMap<>();
        submissions = new HashMap<>();
        basicInfos = new HashMap<>();
        enterpriseProfiles = new HashMap<>();
        surveyEnterprises = new HashMap<>();
        approvalDataScopeService = new ApprovalDataScopeService(
            repository(SysUserRepository.class, (method, args) -> {
                if ("findById".equals(method.getName())) {
                    return Optional.ofNullable(users.get(args[0]));
                }
                throw unexpected(method);
            }),
            repository(SubmissionFormRepository.class, (method, args) -> {
                if ("findById".equals(method.getName())) {
                    return Optional.ofNullable(submissions.get(args[0]));
                }
                throw unexpected(method);
            }),
            repository(SubmissionBasicInfoRepository.class, (method, args) -> {
                if ("findBySubmissionId".equals(method.getName())) {
                    return Optional.ofNullable(basicInfos.get(args[0]));
                }
                throw unexpected(method);
            }),
            repository(EnterpriseProfileRepository.class, (method, args) -> {
                if ("findById".equals(method.getName())) {
                    return Optional.ofNullable(enterpriseProfiles.get(args[0]));
                }
                throw unexpected(method);
            }),
            repository(SurveyEnterpriseRepository.class, (method, args) -> {
                if ("findFirstByEnterpriseNameOrderByIdAsc".equals(method.getName())) {
                    return Optional.ofNullable(surveyEnterprises.get(args[0]));
                }
                throw unexpected(method);
            })
        );
    }

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
        enterpriseProfiles.put(20L, profile);

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
        return new CurrentUser(2L, "cs_monitor", "cs_monitor", null, Set.of("TOWN_MONITOR"));
    }

    private void mockScopeUser(String scope) {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setEnterpriseCodeFirstDigitScope(scope);
        users.put(1L, user);
    }

    private void mockTownMonitorUser(String townStreetCode) {
        SysUser user = new SysUser();
        user.setId(2L);
        user.setTownStreetCodeScope(townStreetCode);
        users.put(2L, user);
    }

    private SubmissionForm submissionForm(Long id, Long enterpriseId) {
        SubmissionForm form = new SubmissionForm();
        form.setId(id);
        form.setEnterpriseId(enterpriseId);
        submissions.put(id, form);
        return form;
    }

    private void mockEnterpriseFirstDigit(Long enterpriseId, String enterpriseName, String firstDigit) {
        EnterpriseProfile profile = new EnterpriseProfile();
        profile.setId(enterpriseId);
        profile.setEnterpriseName(enterpriseName);
        enterpriseProfiles.put(enterpriseId, profile);

        SurveyEnterprise surveyEnterprise = new SurveyEnterprise();
        surveyEnterprise.setEnterpriseName(enterpriseName);
        surveyEnterprise.setEnterpriseCodeFirstDigit(firstDigit);
        surveyEnterprises.put(enterpriseName, surveyEnterprise);
    }

    private void mockEnterpriseTownStreetCode(Long enterpriseId, String enterpriseName, String townStreetCode) {
        EnterpriseProfile profile = new EnterpriseProfile();
        profile.setId(enterpriseId);
        profile.setEnterpriseName(enterpriseName);
        enterpriseProfiles.put(enterpriseId, profile);

        SurveyEnterprise surveyEnterprise = new SurveyEnterprise();
        surveyEnterprise.setEnterpriseName(enterpriseName);
        surveyEnterprise.setTownStreetCode(townStreetCode);
        surveyEnterprises.put(enterpriseName, surveyEnterprise);
    }

    @SuppressWarnings("unchecked")
    private <T> T repository(Class<T> repositoryType, RepositoryCall call) {
        return (T) Proxy.newProxyInstance(
            repositoryType.getClassLoader(),
            new Class<?>[] {repositoryType},
            (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return objectMethod(proxy, method, args, repositoryType);
                }
                return call.invoke(method, args == null ? new Object[0] : args);
            }
        );
    }

    private Object objectMethod(Object proxy, Method method, Object[] args, Class<?> repositoryType) {
        return switch (method.getName()) {
            case "toString" -> repositoryType.getSimpleName() + "TestProxy";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> throw unexpected(method);
        };
    }

    private UnsupportedOperationException unexpected(Method method) {
        return new UnsupportedOperationException("Unexpected repository call: " + method.getName());
    }

    @FunctionalInterface
    private interface RepositoryCall {
        Object invoke(Method method, Object[] args);
    }
}
