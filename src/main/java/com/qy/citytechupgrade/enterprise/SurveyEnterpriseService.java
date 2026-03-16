package com.qy.citytechupgrade.enterprise;

import com.qy.citytechupgrade.common.dto.PagedResult;
import com.qy.citytechupgrade.common.exception.BizException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SurveyEnterpriseService {
    private final SurveyEnterpriseRepository surveyEnterpriseRepository;

    public PagedResult<SurveyEnterprise> list(String townPark, String enterpriseName, String industryCode, Integer page, Integer size) {
        String normalizedTownPark = normalizeKeyword(townPark);
        String normalizedEnterpriseName = normalizeKeyword(enterpriseName);
        String normalizedIndustryCode = normalizeKeyword(industryCode);
        Pageable pageable = buildPageable(page, size);
        Specification<SurveyEnterprise> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!normalizedTownPark.isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("townPark")), "%" + normalizedTownPark + "%"));
            }
            if (!normalizedEnterpriseName.isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("enterpriseName")), "%" + normalizedEnterpriseName + "%"));
            }
            if (!normalizedIndustryCode.isEmpty()) {
                predicates.add(cb.like(cb.lower(cb.coalesce(root.get("industryCode"), "")), "%" + normalizedIndustryCode + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<SurveyEnterprise> result = surveyEnterpriseRepository.findAll(spec, pageable);
        return PagedResult.of(result.getContent(), result.getTotalElements(), result.getNumber() + 1, result.getSize());
    }

    public SurveyEnterprise create(SurveyEnterpriseUpsertRequest request) {
        SurveyEnterprise entity = new SurveyEnterprise();
        apply(entity, request);
        return surveyEnterpriseRepository.save(entity);
    }

    public SurveyEnterprise update(Long id, SurveyEnterpriseUpsertRequest request) {
        SurveyEnterprise entity = surveyEnterpriseRepository.findById(id)
            .orElseThrow(() -> new BizException("调研企业信息不存在"));
        apply(entity, request);
        return surveyEnterpriseRepository.save(entity);
    }

    public void delete(Long id) {
        if (!surveyEnterpriseRepository.existsById(id)) {
            throw new BizException("调研企业信息不存在");
        }
        surveyEnterpriseRepository.deleteById(id);
    }

    private void apply(SurveyEnterprise entity, SurveyEnterpriseUpsertRequest request) {
        entity.setTownPark(normalizeRequired(request.getTownPark(), "所属镇街园区不能为空", 100));
        entity.setEnterpriseName(normalizeRequired(request.getEnterpriseName(), "企业名称不能为空", 255));
        entity.setIndustryCode(normalizeOptional(request.getIndustryCode(), 20));
        entity.setEnterpriseCodeFirstDigit(normalizeOptional(request.getEnterpriseCodeFirstDigit(), 1));
        entity.setEnterpriseCodeTownDigits(normalizeOptional(request.getEnterpriseCodeTownDigits(), 2));
        entity.setEnterpriseCodeIndustryDigits(normalizeOptional(request.getEnterpriseCodeIndustryDigits(), 2));
        entity.setEnterpriseCodeSequenceDigits(normalizeOptional(request.getEnterpriseCodeSequenceDigits(), 3));
        entity.setSourceRowNo(normalizeSourceRowNo(request.getSourceRowNo()));
    }

    private Pageable buildPageable(Integer page, Integer size) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        return PageRequest.of(
            safePage - 1,
            safeSize,
            Sort.by(
                Sort.Order.asc("enterpriseName"),
                Sort.Order.asc("id")
            )
        );
    }

    private String normalizeKeyword(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String normalizeRequired(String value, String message, int maxLen) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(message);
        }
        String normalized = value.trim();
        return normalized.length() <= maxLen ? normalized : normalized.substring(0, maxLen);
    }

    private String normalizeOptional(String value, int maxLen) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLen ? normalized : normalized.substring(0, maxLen);
    }

    private Integer normalizeSourceRowNo(Integer sourceRowNo) {
        if (sourceRowNo == null || sourceRowNo < 0) {
            return 0;
        }
        return sourceRowNo;
    }
}
