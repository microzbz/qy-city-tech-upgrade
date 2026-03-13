package com.qy.citytechupgrade.enterprise;

import com.qy.citytechupgrade.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EnterpriseService {
    private final EnterpriseProfileRepository enterpriseProfileRepository;
    private final QfClientService qfClientService;
    private final JdbcTemplate jdbcTemplate;

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
        String name = enterpriseName == null ? null : enterpriseName.trim();
        if (!StringUtils.hasText(name)) {
            return null;
        }
        try {
            List<String> codes = jdbcTemplate.query(
                "SELECT industry_code FROM survey_enterprise_list WHERE enterprise_name = ? ORDER BY id ASC LIMIT 1",
                (rs, rowNum) -> rs.getString("industry_code"),
                name
            );
            if (codes.isEmpty()) {
                return null;
            }
            String code = codes.get(0);
            return StringUtils.hasText(code) ? code.trim() : null;
        } catch (DataAccessException ex) {
            return null;
        }
    }

    public SurveyEnterpriseCodeInfo findSurveyEnterpriseCodeInfo(String enterpriseName) {
        String name = enterpriseName == null ? null : enterpriseName.trim();
        if (!StringUtils.hasText(name)) {
            return null;
        }
        try {
            List<SurveyEnterpriseCodeInfo> items = jdbcTemplate.query(
                """
                SELECT enterprise_name,
                       industry_code,
                       enterprise_code_first_digit,
                       enterprise_code_town_digits,
                       enterprise_code_industry_digits,
                       enterprise_code_sequence_digits
                FROM survey_enterprise_list
                WHERE enterprise_name = ?
                ORDER BY id ASC
                LIMIT 1
                """,
                (rs, rowNum) -> new SurveyEnterpriseCodeInfo(
                    rs.getString("enterprise_name"),
                    rs.getString("industry_code"),
                    rs.getString("enterprise_code_first_digit"),
                    rs.getString("enterprise_code_town_digits"),
                    rs.getString("enterprise_code_industry_digits"),
                    rs.getString("enterprise_code_sequence_digits")
                ),
                name
            );
            return items.isEmpty() ? null : items.get(0);
        } catch (DataAccessException ex) {
            return null;
        }
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
}
