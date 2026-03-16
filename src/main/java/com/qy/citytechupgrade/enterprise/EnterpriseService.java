package com.qy.citytechupgrade.enterprise;

import com.qy.citytechupgrade.common.exception.BizException;
import com.qy.citytechupgrade.industry.IndustryProcessMap;
import com.qy.citytechupgrade.industry.IndustryProcessMapRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class EnterpriseService {
    private final EnterpriseProfileRepository enterpriseProfileRepository;
    private final QfClientService qfClientService;
    private final IndustryProcessMapRepository industryProcessMapRepository;
    private final SurveyEnterpriseRepository surveyEnterpriseRepository;

    public EnterpriseProfileVO getByCreditCode(String creditCode) {
        EnterpriseProfile profile = enterpriseProfileRepository.findByCreditCode(creditCode).orElse(null);
        if (profile == null) {
            profile = qfClientService.fetchEnterpriseProfile(creditCode);
            enterpriseProfileRepository.save(profile);
        }
        return EnterpriseProfileVO.from(profile);
    }

    public EnterpriseProfile findByIdOrThrow(Long id) {
        return enterpriseProfileRepository.findById(id).orElseThrow(() -> new BizException("企业不存在"));
    }

    public EnterpriseProfile save(EnterpriseProfile profile) {
        return enterpriseProfileRepository.save(profile);
    }

    public String findIndustryCodeByEnterpriseName(String enterpriseName) {
        SurveyEnterprise surveyEnterprise = findSurveyEnterprise(enterpriseName);
        if (surveyEnterprise == null || !StringUtils.hasText(surveyEnterprise.getIndustryCode())) {
            return null;
        }
        return surveyEnterprise.getIndustryCode().trim();
    }

    public SurveyEnterpriseCodeInfo findSurveyEnterpriseCodeInfo(String enterpriseName) {
        SurveyEnterprise surveyEnterprise = findSurveyEnterprise(enterpriseName);
        if (surveyEnterprise == null) {
            return null;
        }
        return new SurveyEnterpriseCodeInfo(
            surveyEnterprise.getEnterpriseName(),
            surveyEnterprise.getIndustryCode(),
            surveyEnterprise.getEnterpriseCodeFirstDigit(),
            surveyEnterprise.getEnterpriseCodeTownDigits(),
            surveyEnterprise.getEnterpriseCodeIndustryDigits(),
            surveyEnterprise.getEnterpriseCodeSequenceDigits()
        );
    }

    public SurveyIndustryInfo findSurveyIndustryInfo(String enterpriseName) {
        SurveyEnterpriseCodeInfo codeInfo = findSurveyEnterpriseCodeInfo(enterpriseName);
        if (codeInfo == null || !StringUtils.hasText(codeInfo.industryCode())) {
            return null;
        }
        String industryCode = codeInfo.industryCode().trim();
        String industryName = findIndustryNameByIndustryCode(industryCode);
        return new SurveyIndustryInfo(industryCode, industryName);
    }

    public String findIndustryNameByIndustryCode(String industryCode) {
        String code = industryCode == null ? null : industryCode.trim();
        if (!StringUtils.hasText(code)) {
            return null;
        }
        if (code.length() >= 3) {
            String threeDigits = code.substring(0, 3);
            String matched = findIndustryNameByExactCode(threeDigits);
            if (StringUtils.hasText(matched)) {
                return matched;
            }
        }
        if (code.length() >= 2) {
            String twoDigits = code.substring(0, 2);
            String matched = findIndustryNameByExactCode(twoDigits);
            if (StringUtils.hasText(matched)) {
                return matched;
            }
        }
        return findIndustryNameByExactCode(code);
    }

    private String findIndustryNameByExactCode(String industryCode) {
        return industryProcessMapRepository.findByIndustryCode(industryCode)
            .map(IndustryProcessMap::getIndustryName)
            .map(String::trim)
            .filter(StringUtils::hasText)
            .orElse(null);
    }

    private SurveyEnterprise findSurveyEnterprise(String enterpriseName) {
        String name = enterpriseName == null ? null : enterpriseName.trim();
        if (!StringUtils.hasText(name)) {
            return null;
        }
        return surveyEnterpriseRepository.findFirstByEnterpriseNameOrderByIdAsc(name).orElse(null);
    }

    public record SurveyEnterpriseCodeInfo(
        String enterpriseName,
        String industryCode,
        String firstDigit,
        String townDigits,
        String industryDigits,
        String sequenceDigits
    ) {
        public String exportCode() {
            return safe(firstDigit) + safe(townDigits) + safe(industryDigits) + safe(sequenceDigits);
        }

        private static String safe(String value) {
            return value == null ? "" : value.trim();
        }
    }

    public record SurveyIndustryInfo(
        String industryCode,
        String industryName
    ) {
    }
}
