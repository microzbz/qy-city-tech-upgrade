package com.qy.citytechupgrade.industry;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class IndustryService {
    private final IndustryProcessMapRepository industryProcessMapRepository;
    private final ProcessEquipmentMapRepository processEquipmentMapRepository;

    public IndustryProcessOptionsResponse listProcessOptions(String industryCode) {
        String code = normalizeRequired(industryCode, "行业代码不能为空");
        Optional<IndustryProcessMap> exactMapping = industryProcessMapRepository.findByIndustryCode(code);
        if (exactMapping.isPresent() && Boolean.TRUE.equals(exactMapping.get().getSpecialMode())) {
            return new IndustryProcessOptionsResponse(
                splitSpecialProcesses(exactMapping.get().getProcessNamesText()),
                true
            );
        }

        Set<String> merged = new LinkedHashSet<>();

        if (code.length() >= 2) {
            String majorCode = code.substring(0, 2);
            addProcessesByIndustryCode(merged, majorCode);
        }

        if (code.length() < 2 || !code.substring(0, 2).equals(code)) {
            addProcessesByIndustryCode(merged, code);
        }

        return new IndustryProcessOptionsResponse(new ArrayList<>(merged), false);
    }

    public List<String> listEquipments(String industryCode, String processName) {
        String code = normalizeOptionalIndustryCode(industryCode);
        Optional<IndustryProcessMap> exactMapping = StringUtils.hasText(code)
            ? industryProcessMapRepository.findByIndustryCode(code)
            : Optional.empty();
        if (exactMapping.isPresent() && Boolean.TRUE.equals(exactMapping.get().getSpecialMode())) {
            return listSpecialModeEquipments(code, exactMapping.get().getProcessNamesText());
        }

        String process = normalizeText(normalizeRequired(processName, "工序名称不能为空"));
        Set<String> merged = new LinkedHashSet<>();
        appendEquipments(merged, processEquipmentMapRepository.findAllByProcessNameAndIndustryCodeIsNullOrderByIdAsc(process));
        if (StringUtils.hasText(code)) {
            appendEquipments(merged, processEquipmentMapRepository.findAllByProcessNameAndIndustryCodeOrderByIdAsc(process, code));
        }
        return new ArrayList<>(merged);
    }

    public PagedResult<IndustryProcessMap> listProcessMappings(String industryCode, String processName, Integer page, Integer size) {
        String code = normalizeKeyword(industryCode);
        String process = normalizeKeyword(processName);
        Pageable pageable = buildPageable(
            page,
            size,
            Sort.by(
                Sort.Order.asc("industryCode"),
                Sort.Order.asc("id")
            )
        );
        Specification<IndustryProcessMap> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!code.isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("industryCode")), "%" + code + "%"));
            }
            if (!process.isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("processNamesText")), "%" + process + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<IndustryProcessMap> result = industryProcessMapRepository.findAll(spec, pageable);
        return PagedResult.of(result.getContent(), result.getTotalElements(), result.getNumber() + 1, result.getSize());
    }

    public IndustryProcessMap createProcessMapping(IndustryProcessMappingUpsertRequest request) {
        IndustryProcessMap entity = new IndustryProcessMap();
        applyProcessMapping(entity, request);
        return saveProcessMapping(entity);
    }

    public IndustryProcessMap updateProcessMapping(Long id, IndustryProcessMappingUpsertRequest request) {
        IndustryProcessMap entity = industryProcessMapRepository.findById(id)
            .orElseThrow(() -> new BizException("行业工序映射不存在"));
        applyProcessMapping(entity, request);
        return saveProcessMapping(entity);
    }

    public void deleteProcessMapping(Long id) {
        if (!industryProcessMapRepository.existsById(id)) {
            throw new BizException("行业工序映射不存在");
        }
        industryProcessMapRepository.deleteById(id);
    }

    public PagedResult<ProcessEquipmentMap> listEquipmentMappings(String industryCode,
                                                                  String processName,
                                                                  String equipmentName,
                                                                  Integer page,
                                                                  Integer size) {
        String code = normalizeKeyword(industryCode);
        String process = normalizeKeyword(processName);
        String equipment = normalizeKeyword(equipmentName);
        Pageable pageable = buildPageable(
            page,
            size,
            Sort.by(
                Sort.Order.asc("industryCode"),
                Sort.Order.asc("processName"),
                Sort.Order.asc("id")
            )
        );
        Specification<ProcessEquipmentMap> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!code.isEmpty()) {
                predicates.add(cb.like(cb.lower(cb.coalesce(root.get("industryCode"), "")), "%" + code + "%"));
            }
            if (!process.isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("processName")), "%" + process + "%"));
            }
            if (!equipment.isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("equipmentNamesText")), "%" + equipment + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<ProcessEquipmentMap> result = processEquipmentMapRepository.findAll(spec, pageable);
        return PagedResult.of(result.getContent(), result.getTotalElements(), result.getNumber() + 1, result.getSize());
    }

    public ProcessEquipmentMap createEquipmentMapping(ProcessEquipmentMappingUpsertRequest request) {
        ProcessEquipmentMap entity = new ProcessEquipmentMap();
        applyEquipmentMapping(entity, request);
        assertUniqueEquipmentMapping(entity.getProcessName(), entity.getIndustryCode(), null);
        return saveEquipmentMapping(entity);
    }

    public ProcessEquipmentMap updateEquipmentMapping(Long id, ProcessEquipmentMappingUpsertRequest request) {
        ProcessEquipmentMap entity = processEquipmentMapRepository.findById(id)
            .orElseThrow(() -> new BizException("工序设备映射不存在"));
        applyEquipmentMapping(entity, request);
        assertUniqueEquipmentMapping(entity.getProcessName(), entity.getIndustryCode(), id);
        return saveEquipmentMapping(entity);
    }

    public void deleteEquipmentMapping(Long id) {
        if (!processEquipmentMapRepository.existsById(id)) {
            throw new BizException("工序设备映射不存在");
        }
        processEquipmentMapRepository.deleteById(id);
    }

    private IndustryProcessMap saveProcessMapping(IndustryProcessMap entity) {
        try {
            return industryProcessMapRepository.save(entity);
        } catch (DataIntegrityViolationException ex) {
            throw new BizException("行业代码已存在映射，请直接编辑");
        }
    }

    private ProcessEquipmentMap saveEquipmentMapping(ProcessEquipmentMap entity) {
        try {
            return processEquipmentMapRepository.save(entity);
        } catch (DataIntegrityViolationException ex) {
            throw new BizException("工序设备映射保存失败，请检查数据后重试");
        }
    }

    private void assertUniqueEquipmentMapping(String processName, String industryCode, Long selfId) {
        boolean exists;
        if (!StringUtils.hasText(industryCode)) {
            exists = selfId == null
                ? processEquipmentMapRepository.existsByProcessNameAndIndustryCodeIsNull(processName)
                : processEquipmentMapRepository.existsByProcessNameAndIndustryCodeIsNullAndIdNot(processName, selfId);
        } else {
            exists = selfId == null
                ? processEquipmentMapRepository.existsByProcessNameAndIndustryCode(processName, industryCode)
                : processEquipmentMapRepository.existsByProcessNameAndIndustryCodeAndIdNot(processName, industryCode, selfId);
        }
        if (exists) {
            throw new BizException("相同行业代码下的主要工序已存在，请直接编辑");
        }
    }

    private void applyProcessMapping(IndustryProcessMap entity, IndustryProcessMappingUpsertRequest request) {
        entity.setIndustryCode(normalizeRequired(request.getIndustryCode(), "行业代码不能为空"));
        entity.setIndustryName(normalizeRequired(request.getIndustryName(), "行业名称不能为空"));
        boolean specialMode = Boolean.TRUE.equals(request.getSpecialMode());
        entity.setSpecialMode(specialMode);
        entity.setProcessNamesText(specialMode
            ? normalizeSpecialProcessNamesText(request.getProcessNamesText())
            : normalizeProcessNamesText(request.getProcessNamesText()));
    }

    private void applyEquipmentMapping(ProcessEquipmentMap entity, ProcessEquipmentMappingUpsertRequest request) {
        entity.setProcessName(normalizeText(normalizeRequired(request.getProcessName(), "主要工序不能为空")));
        entity.setIndustryCode(normalizeOptionalIndustryCode(request.getIndustryCode()));
        entity.setEquipmentNamesText(normalizeEquipmentNamesText(request.getEquipmentNamesText()));
    }

    private String normalizeProcessNamesText(String raw) {
        List<String> parts = splitBySeparators(raw, "[;；]+");
        if (parts.isEmpty()) {
            throw new BizException("主要工序不能为空");
        }
        return String.join(";", parts);
    }

    private String normalizeSpecialProcessNamesText(String raw) {
        List<String> parts = splitBySeparators(raw, "[、,，;；]+");
        if (parts.isEmpty()) {
            throw new BizException("主要工序不能为空");
        }
        return String.join(";", parts);
    }

    private String normalizeEquipmentNamesText(String raw) {
        List<String> parts = splitBySeparators(raw, "[、,，]+");
        if (parts.isEmpty()) {
            throw new BizException("主要设备不能为空");
        }
        return String.join("、", parts);
    }

    private List<String> splitBySeparators(String raw, String regex) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        String normalized = raw.trim().replace("\n", ";").replace("\r", ";");
        String[] arr = normalized.split(regex);
        Set<String> out = new LinkedHashSet<>();
        for (String item : arr) {
            if (!StringUtils.hasText(item)) {
                continue;
            }
            String normalizedItem = normalizeText(item);
            if (StringUtils.hasText(normalizedItem)) {
                out.add(normalizedItem);
            }
        }
        return new ArrayList<>(out);
    }

    private void addProcessesByIndustryCode(Set<String> merged, String industryCode) {
        Optional<IndustryProcessMap> mapping = industryProcessMapRepository.findByIndustryCode(industryCode);
        if (mapping.isEmpty()) {
            return;
        }
        merged.addAll(splitBySeparators(mapping.get().getProcessNamesText(), "[;；]+"));
    }

    private void appendEquipments(Set<String> merged, List<ProcessEquipmentMap> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            return;
        }
        for (ProcessEquipmentMap mapping : mappings) {
            merged.addAll(splitBySeparators(mapping.getEquipmentNamesText(), "[、,，]+"));
        }
    }

    private List<String> listSpecialModeEquipments(String industryCode, String processNamesText) {
        Set<String> merged = new LinkedHashSet<>();
        if (StringUtils.hasText(industryCode)) {
            appendEquipments(merged, processEquipmentMapRepository.findAllByIndustryCodeOrderByIdAsc(industryCode));
        }
        if (merged.isEmpty() && StringUtils.hasText(processNamesText)) {
            appendEquipments(merged, processEquipmentMapRepository.findAllByProcessNameOrderByIdAsc(normalizeText(processNamesText)));
        }
        return new ArrayList<>(merged);
    }

    private List<String> splitSpecialProcesses(String raw) {
        return splitBySeparators(raw, "[、,，;；]+");
    }

    private String normalizeRequired(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(message);
        }
        return value.trim();
    }

    private String normalizeOptionalIndustryCode(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeKeyword(String value) {
        return value == null ? "" : normalizeText(value).toLowerCase();
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("\u200B", "")
            .replace("\u200C", "")
            .replace("\u200D", "")
            .replace("\uFEFF", "")
            .trim();
    }

    private Pageable buildPageable(Integer page, Integer size, Sort sort) {
        int safeSize = size == null ? 20 : Math.min(Math.max(size, 1), 200);
        int safePage = page == null ? 1 : Math.max(page, 1);
        return PageRequest.of(safePage - 1, safeSize, sort);
    }
}
