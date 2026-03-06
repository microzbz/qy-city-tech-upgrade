package com.qy.citytechupgrade.submission.option;

import com.qy.citytechupgrade.common.dto.PagedResult;
import com.qy.citytechupgrade.common.exception.BizException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SubmissionOptionService {
    private final DigitalSystemOptionRepository digitalSystemOptionRepository;
    private final RdToolOptionRepository rdToolOptionRepository;

    public List<SubmissionOptionItem> listDigitalSystemOptions() {
        return digitalSystemOptionRepository.findByEnabledTrueOrderBySortNoAscIdAsc().stream()
            .map(this::toDigitalItem)
            .toList();
    }

    public List<SubmissionOptionItem> listRdToolOptions() {
        return rdToolOptionRepository.findByEnabledTrueOrderBySortNoAscIdAsc().stream()
            .map(this::toRdToolItem)
            .toList();
    }

    public String getDigitalOtherOptionName() {
        return digitalSystemOptionRepository.findFirstByEnabledTrueAndOtherOptionTrueOrderBySortNoAscIdAsc()
            .map(DigitalSystemOption::getOptionName)
            .orElse(null);
    }

    public String getRdToolOtherOptionName() {
        return rdToolOptionRepository.findFirstByEnabledTrueAndOtherOptionTrueOrderBySortNoAscIdAsc()
            .map(RdToolOption::getOptionName)
            .orElse(null);
    }

    public PagedResult<DigitalSystemOption> listDigitalSystemOptionsForAdmin(String optionName, Boolean enabled, Integer page, Integer size) {
        Pageable pageable = buildPageable(
            page,
            size,
            Sort.by(Sort.Order.asc("sortNo"), Sort.Order.asc("id"))
        );
        Specification<DigitalSystemOption> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            String keyword = normalizeKeyword(optionName);
            if (!keyword.isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("optionName")), "%" + keyword + "%"));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<DigitalSystemOption> result = digitalSystemOptionRepository.findAll(spec, pageable);
        return PagedResult.of(result.getContent(), result.getTotalElements(), result.getNumber() + 1, result.getSize());
    }

    public PagedResult<RdToolOption> listRdToolOptionsForAdmin(String optionName, Boolean enabled, Integer page, Integer size) {
        Pageable pageable = buildPageable(
            page,
            size,
            Sort.by(Sort.Order.asc("sortNo"), Sort.Order.asc("id"))
        );
        Specification<RdToolOption> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            String keyword = normalizeKeyword(optionName);
            if (!keyword.isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("optionName")), "%" + keyword + "%"));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<RdToolOption> result = rdToolOptionRepository.findAll(spec, pageable);
        return PagedResult.of(result.getContent(), result.getTotalElements(), result.getNumber() + 1, result.getSize());
    }

    public DigitalSystemOption createDigitalSystemOption(SubmissionOptionUpsertRequest request) {
        DigitalSystemOption entity = new DigitalSystemOption();
        applyDigitalOption(entity, request);
        return saveDigitalOption(entity);
    }

    public DigitalSystemOption updateDigitalSystemOption(Long id, SubmissionOptionUpsertRequest request) {
        DigitalSystemOption entity = digitalSystemOptionRepository.findById(id)
            .orElseThrow(() -> new BizException("数字化系统选项不存在"));
        applyDigitalOption(entity, request);
        return saveDigitalOption(entity);
    }

    public void deleteDigitalSystemOption(Long id) {
        if (!digitalSystemOptionRepository.existsById(id)) {
            throw new BizException("数字化系统选项不存在");
        }
        digitalSystemOptionRepository.deleteById(id);
    }

    public RdToolOption createRdToolOption(SubmissionOptionUpsertRequest request) {
        RdToolOption entity = new RdToolOption();
        applyRdToolOption(entity, request);
        return saveRdToolOption(entity);
    }

    public RdToolOption updateRdToolOption(Long id, SubmissionOptionUpsertRequest request) {
        RdToolOption entity = rdToolOptionRepository.findById(id)
            .orElseThrow(() -> new BizException("研发工具选项不存在"));
        applyRdToolOption(entity, request);
        return saveRdToolOption(entity);
    }

    public void deleteRdToolOption(Long id) {
        if (!rdToolOptionRepository.existsById(id)) {
            throw new BizException("研发工具选项不存在");
        }
        rdToolOptionRepository.deleteById(id);
    }

    private DigitalSystemOption saveDigitalOption(DigitalSystemOption entity) {
        checkDigitalOtherUnique(entity.getId(), entity.getOtherOption());
        try {
            return digitalSystemOptionRepository.save(entity);
        } catch (DataIntegrityViolationException ex) {
            throw new BizException("数字化系统选项名称已存在");
        }
    }

    private RdToolOption saveRdToolOption(RdToolOption entity) {
        checkRdOtherUnique(entity.getId(), entity.getOtherOption());
        try {
            return rdToolOptionRepository.save(entity);
        } catch (DataIntegrityViolationException ex) {
            throw new BizException("研发工具选项名称已存在");
        }
    }

    private void applyDigitalOption(DigitalSystemOption entity, SubmissionOptionUpsertRequest request) {
        entity.setOptionName(normalizeRequired(request.getOptionName(), "选项名称不能为空"));
        entity.setSortNo(request.getSortNo());
        entity.setOtherOption(Boolean.TRUE.equals(request.getOtherOption()));
        entity.setEnabled(!Boolean.FALSE.equals(request.getEnabled()));
    }

    private void applyRdToolOption(RdToolOption entity, SubmissionOptionUpsertRequest request) {
        entity.setOptionName(normalizeRequired(request.getOptionName(), "选项名称不能为空"));
        entity.setSortNo(request.getSortNo());
        entity.setOtherOption(Boolean.TRUE.equals(request.getOtherOption()));
        entity.setEnabled(!Boolean.FALSE.equals(request.getEnabled()));
    }

    private void checkDigitalOtherUnique(Long selfId, Boolean otherOption) {
        if (!Boolean.TRUE.equals(otherOption)) {
            return;
        }
        digitalSystemOptionRepository.findFirstByOtherOptionTrue()
            .ifPresent(existing -> {
                if (!Objects.equals(existing.getId(), selfId)) {
                    throw new BizException("数字化系统“其他”选项只能保留一条");
                }
            });
    }

    private void checkRdOtherUnique(Long selfId, Boolean otherOption) {
        if (!Boolean.TRUE.equals(otherOption)) {
            return;
        }
        rdToolOptionRepository.findFirstByOtherOptionTrue()
            .ifPresent(existing -> {
                if (!Objects.equals(existing.getId(), selfId)) {
                    throw new BizException("研发工具“其他”选项只能保留一条");
                }
            });
    }

    private Pageable buildPageable(Integer page, Integer size, Sort sort) {
        int safeSize = size == null ? 20 : Math.min(Math.max(size, 1), 200);
        int safePage = page == null ? 1 : Math.max(page, 1);
        return PageRequest.of(safePage - 1, safeSize, sort);
    }

    private String normalizeKeyword(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String normalizeRequired(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(message);
        }
        return value.trim();
    }

    private SubmissionOptionItem toDigitalItem(DigitalSystemOption option) {
        return new SubmissionOptionItem(
            option.getId(),
            option.getOptionName(),
            option.getSortNo(),
            Boolean.TRUE.equals(option.getOtherOption())
        );
    }

    private SubmissionOptionItem toRdToolItem(RdToolOption option) {
        return new SubmissionOptionItem(
            option.getId(),
            option.getOptionName(),
            option.getSortNo(),
            Boolean.TRUE.equals(option.getOtherOption())
        );
    }
}
