package com.qy.citytechupgrade.enterprise;

import com.qy.citytechupgrade.common.exception.BizException;
import com.qy.citytechupgrade.common.security.CurrentUser;
import com.qy.citytechupgrade.industry.IndustryProcessMapRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnterpriseServiceTest {
    @Mock
    private EnterpriseProfileRepository enterpriseProfileRepository;
    @Mock
    private QfClientService qfClientService;
    @Mock
    private IndustryProcessMapRepository industryProcessMapRepository;
    @Mock
    private SurveyEnterpriseRepository surveyEnterpriseRepository;

    private EnterpriseService enterpriseService;

    @BeforeEach
    void setUp() {
        enterpriseService = new EnterpriseService(
            enterpriseProfileRepository,
            qfClientService,
            industryProcessMapRepository,
            surveyEnterpriseRepository
        );
    }

    @Test
    void updateCurrentEnterpriseContactUpdatesBoundEnterprisePushReceiver() {
        CurrentUser currentUser = enterpriseUser(108L);
        EnterpriseProfile profile = new EnterpriseProfile();
        profile.setId(108L);
        profile.setEnterpriseName("广东晓鸟动力技术有限公司");
        profile.setCreditCode("91441900MACWMNGL4W");
        profile.setContactName("刘艺");
        profile.setContactCertNo("441622198407086484");
        profile.setContactCertType("10");
        when(enterpriseProfileRepository.findById(108L)).thenReturn(Optional.of(profile));
        when(enterpriseProfileRepository.save(profile)).thenReturn(profile);

        EnterpriseContactUpdateRequest request = new EnterpriseContactUpdateRequest();
        request.setContactName("  李四  ");
        request.setContactCertNo("  441900199001011231  ");

        EnterpriseProfileVO updated = enterpriseService.updateCurrentEnterpriseContact(currentUser, request);

        assertThat(updated.getContactName()).isEqualTo("李四");
        assertThat(updated.getContactCertNo()).isEqualTo("441900199001011231");
        assertThat(updated.getContactCertType()).isEqualTo("10");
        verify(enterpriseProfileRepository).save(profile);
    }

    @Test
    void updateCurrentEnterpriseContactRejectsBlankCertNo() {
        CurrentUser currentUser = enterpriseUser(108L);
        EnterpriseContactUpdateRequest request = new EnterpriseContactUpdateRequest();
        request.setContactName("李四");
        request.setContactCertNo(" ");

        assertThatThrownBy(() -> enterpriseService.updateCurrentEnterpriseContact(currentUser, request))
            .isInstanceOf(BizException.class)
            .hasMessage("联系人身份证号不能为空");
        verifyNoInteractions(enterpriseProfileRepository);
    }

    @Test
    void updateCurrentEnterpriseContactAllowsNonResidentIdCardFormat() {
        CurrentUser currentUser = enterpriseUser(108L);
        EnterpriseProfile profile = new EnterpriseProfile();
        profile.setId(108L);
        profile.setEnterpriseName("广东晓鸟动力技术有限公司");
        profile.setCreditCode("91441900MACWMNGL4W");
        when(enterpriseProfileRepository.findById(108L)).thenReturn(Optional.of(profile));
        when(enterpriseProfileRepository.save(profile)).thenReturn(profile);

        EnterpriseContactUpdateRequest request = new EnterpriseContactUpdateRequest();
        request.setContactName("李四");
        request.setContactCertNo("  ABC-123456  ");

        EnterpriseProfileVO updated = enterpriseService.updateCurrentEnterpriseContact(currentUser, request);

        assertThat(updated.getContactCertNo()).isEqualTo("ABC-123456");
        verify(enterpriseProfileRepository).save(profile);
    }

    private CurrentUser enterpriseUser(Long enterpriseId) {
        return new CurrentUser(7L, "enterprise", "企业用户", enterpriseId, Set.of("ENTERPRISE_USER"));
    }
}
