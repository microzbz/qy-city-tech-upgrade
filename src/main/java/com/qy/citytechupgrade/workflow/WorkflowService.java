package com.qy.citytechupgrade.workflow;

import com.qy.citytechupgrade.common.enums.WorkflowStatus;
import com.qy.citytechupgrade.common.exception.BizException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WorkflowService {
    public static final String BIZ_TYPE_SUBMISSION = "SUBMISSION";

    private final WfTemplateRepository wfTemplateRepository;
    private final WfTemplateNodeRepository wfTemplateNodeRepository;
    private final WfInstanceRepository wfInstanceRepository;

    public List<WorkflowTemplateDTO> listByBusinessType(String businessType) {
        return wfTemplateRepository.findByBusinessTypeOrderByUpdatedAtDesc(businessType).stream()
            .map(this::toDto)
            .toList();
    }

    public WfTemplate getActiveTemplateOrThrow(String businessType) {
        return wfTemplateRepository.findFirstByBusinessTypeAndActiveTrueOrderByUpdatedAtDesc(businessType)
            .orElseThrow(() -> new BizException("未配置可用审批流程模板"));
    }

    public boolean isApprovalEnabled(String businessType) {
        return wfTemplateRepository.findFirstByBusinessTypeAndActiveTrueOrderByUpdatedAtDesc(businessType)
            .map(t -> Boolean.TRUE.equals(t.getApprovalEnabled()))
            .orElse(true);
    }

    public List<WfTemplateNode> listTemplateNodes(Long templateId) {
        List<WfTemplateNode> nodes = wfTemplateNodeRepository.findByTemplateIdOrderByNodeSeqAsc(templateId);
        if (nodes.isEmpty()) {
            throw new BizException("流程模板未配置节点");
        }
        return nodes;
    }

    @Transactional
    public WorkflowTemplateDTO create(WorkflowTemplateUpsertRequest req, Long operatorUserId) {
        normalizeAndValidate(req);
        WfTemplate template = new WfTemplate();
        template.setBusinessType(req.getBusinessType());
        template.setTemplateName(req.getTemplateName());
        template.setActive(req.getActive() == null ? Boolean.TRUE : req.getActive());
        template.setApprovalEnabled(req.getApprovalEnabled() == null ? Boolean.TRUE : req.getApprovalEnabled());
        template.setCreatedBy(operatorUserId);
        template = wfTemplateRepository.save(template);

        saveNodes(template.getId(), req.getNodes());
        if (Boolean.TRUE.equals(template.getActive())) {
            deactivateOthers(template.getBusinessType(), template.getId());
        }
        return toDto(template);
    }

    @Transactional
    public WorkflowTemplateDTO update(Long templateId, WorkflowTemplateUpsertRequest req) {
        normalizeAndValidate(req);
        assertTemplateEditable(templateId);
        WfTemplate template = wfTemplateRepository.findById(templateId)
            .orElseThrow(() -> new BizException("模板不存在"));
        template.setTemplateName(req.getTemplateName());
        template.setBusinessType(req.getBusinessType());
        template.setActive(req.getActive() == null ? template.getActive() : req.getActive());
        template.setApprovalEnabled(req.getApprovalEnabled() == null ? template.getApprovalEnabled() : req.getApprovalEnabled());
        wfTemplateRepository.save(template);

        wfTemplateNodeRepository.deleteByTemplateId(templateId);
        // Ensure old rows are physically removed before re-inserting same node_seq.
        wfTemplateNodeRepository.flush();
        saveNodes(templateId, req.getNodes());
        if (Boolean.TRUE.equals(template.getActive())) {
            deactivateOthers(template.getBusinessType(), template.getId());
        }
        return toDto(template);
    }

    @Transactional
    public WorkflowTemplateDTO activate(Long templateId) {
        assertTemplateEditable(templateId);
        WfTemplate template = wfTemplateRepository.findById(templateId)
            .orElseThrow(() -> new BizException("模板不存在"));
        template.setActive(true);
        wfTemplateRepository.save(template);
        deactivateOthers(template.getBusinessType(), template.getId());
        return toDto(template);
    }

    @Transactional
    public void delete(Long templateId) {
        WfTemplate template = wfTemplateRepository.findById(templateId)
            .orElseThrow(() -> new BizException("模板不存在"));
        assertTemplateEditable(templateId);
        if (Boolean.TRUE.equals(template.getActive())) {
            throw new BizException("启用中的模板不能删除，请先切换启用其他模板");
        }
        if (wfInstanceRepository.existsByTemplateId(templateId)) {
            throw new BizException("该模板已有审批记录，不能删除");
        }
        wfTemplateNodeRepository.deleteByTemplateId(templateId);
        wfTemplateNodeRepository.flush();
        wfTemplateRepository.delete(template);
    }

    private void saveNodes(Long templateId, List<WorkflowTemplateNodeDTO> nodes) {
        List<WorkflowTemplateNodeDTO> sorted = nodes.stream()
            .sorted(Comparator.comparing(WorkflowTemplateNodeDTO::getNodeSeq))
            .toList();
        try {
            for (WorkflowTemplateNodeDTO node : sorted) {
                WfTemplateNode n = new WfTemplateNode();
                n.setTemplateId(templateId);
                n.setNodeSeq(node.getNodeSeq());
                n.setNodeName(node.getNodeName());
                n.setRoleCode(node.getRoleCode());
                n.setAllowApprove(node.getAllowApprove());
                n.setAllowReject(node.getAllowReject());
                n.setAllowReturn(node.getAllowReturn());
                wfTemplateNodeRepository.save(n);
            }
        } catch (DataIntegrityViolationException ex) {
            throw new BizException("节点顺序重复，请检查后重试");
        }
    }

    private void deactivateOthers(String businessType, Long keepId) {
        List<WfTemplate> templates = wfTemplateRepository.findByBusinessTypeOrderByUpdatedAtDesc(businessType);
        for (WfTemplate t : templates) {
            if (!t.getId().equals(keepId) && Boolean.TRUE.equals(t.getActive())) {
                assertTemplateEditable(t.getId());
                t.setActive(false);
                wfTemplateRepository.save(t);
            }
        }
    }

    private WorkflowTemplateDTO toDto(WfTemplate t) {
        List<WorkflowTemplateNodeDTO> nodes = wfTemplateNodeRepository.findByTemplateIdOrderByNodeSeqAsc(t.getId()).stream()
            .map(n -> {
                WorkflowTemplateNodeDTO dto = new WorkflowTemplateNodeDTO();
                dto.setNodeSeq(n.getNodeSeq());
                dto.setNodeName(n.getNodeName());
                dto.setRoleCode(n.getRoleCode());
                dto.setAllowApprove(n.getAllowApprove());
                dto.setAllowReject(n.getAllowReject());
                dto.setAllowReturn(n.getAllowReturn());
                return dto;
            }).toList();

        return WorkflowTemplateDTO.builder()
            .id(t.getId())
            .businessType(t.getBusinessType())
            .templateName(t.getTemplateName())
            .active(t.getActive())
            .approvalEnabled(t.getApprovalEnabled())
            .nodes(nodes)
            .build();
    }

    private void normalizeAndValidate(WorkflowTemplateUpsertRequest req) {
        req.setBusinessType(trimOrDefault(req.getBusinessType(), BIZ_TYPE_SUBMISSION));
        req.setTemplateName(trimOrDefault(req.getTemplateName(), null));
        if (req.getApprovalEnabled() == null) {
            req.setApprovalEnabled(Boolean.TRUE);
        }
        if (!StringUtils.hasText(req.getTemplateName())) {
            throw new BizException("模板名称不能为空");
        }
        if (req.getNodes() == null || req.getNodes().isEmpty()) {
            throw new BizException("请至少配置一个审批节点");
        }

        Set<Integer> seqSet = new HashSet<>();
        for (int i = 0; i < req.getNodes().size(); i++) {
            WorkflowTemplateNodeDTO node = req.getNodes().get(i);
            if (node == null) {
                throw new BizException("第 " + (i + 1) + " 个节点为空");
            }
            if (node.getNodeSeq() == null || node.getNodeSeq() <= 0) {
                throw new BizException("第 " + (i + 1) + " 个节点顺序必须为正整数");
            }
            if (!seqSet.add(node.getNodeSeq())) {
                throw new BizException("节点顺序不能重复: " + node.getNodeSeq());
            }

            node.setNodeName(trimOrDefault(node.getNodeName(), null));
            node.setRoleCode(trimOrDefault(node.getRoleCode(), null));
            if (!StringUtils.hasText(node.getNodeName())) {
                throw new BizException("第 " + (i + 1) + " 个节点名称不能为空");
            }
            if (!StringUtils.hasText(node.getRoleCode())) {
                throw new BizException("第 " + (i + 1) + " 个审批角色不能为空");
            }

            if (node.getAllowApprove() == null) {
                node.setAllowApprove(true);
            }
            if (node.getAllowReject() == null) {
                node.setAllowReject(true);
            }
            if (node.getAllowReturn() == null) {
                node.setAllowReturn(true);
            }
        }
    }

    private String trimOrDefault(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private void assertTemplateEditable(Long templateId) {
        if (wfInstanceRepository.existsByTemplateIdAndStatus(templateId, WorkflowStatus.RUNNING)) {
            throw new BizException("该模板存在运行中的审批单据，暂不允许改动");
        }
    }
}
