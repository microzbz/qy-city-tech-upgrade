package com.qy.citytechupgrade.submission;

import com.qy.citytechupgrade.audit.AuditService;
import com.qy.citytechupgrade.common.enums.TaskAction;
import com.qy.citytechupgrade.common.enums.TaskStatus;
import com.qy.citytechupgrade.common.enums.SubmissionStatus;
import com.qy.citytechupgrade.common.exception.BizException;
import com.qy.citytechupgrade.common.security.CurrentUser;
import com.qy.citytechupgrade.common.util.JsonUtils;
import com.qy.citytechupgrade.common.util.SubmissionNoUtils;
import com.qy.citytechupgrade.enterprise.EnterpriseProfile;
import com.qy.citytechupgrade.enterprise.EnterpriseService;
import com.qy.citytechupgrade.submission.option.SubmissionOptionService;
import com.qy.citytechupgrade.workflow.WfInstance;
import com.qy.citytechupgrade.workflow.WfInstanceRepository;
import com.qy.citytechupgrade.workflow.WfTask;
import com.qy.citytechupgrade.workflow.WfTaskRepository;
import com.qy.citytechupgrade.workflow.WfTemplateNodeRepository;
import com.qy.citytechupgrade.workflow.WorkflowService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SubmissionService {
    private static final String OTHER_OPTION = "其他";
    private static final String ATTACHMENT_DEVICE = "DEVICE_PROOF";
    private static final String ATTACHMENT_DIGITAL = "DIGITAL_PROOF";
    private static final String ATTACHMENT_RD_TOOL = "RD_TOOL_PROOF";
    private static final String ATTACHMENT_LEGACY = "PROOF";

    private final SubmissionFormRepository submissionFormRepository;
    private final SubmissionBasicInfoRepository submissionBasicInfoRepository;
    private final SubmissionDeviceInfoRepository submissionDeviceInfoRepository;
    private final SubmissionDigitalInfoRepository submissionDigitalInfoRepository;
    private final SubmissionRdToolInfoRepository submissionRdToolInfoRepository;
    private final SubmissionAttachmentRepository submissionAttachmentRepository;
    private final JsonUtils jsonUtils;
    private final EnterpriseService enterpriseService;
    private final AuditService auditService;
    private final WfInstanceRepository wfInstanceRepository;
    private final WfTaskRepository wfTaskRepository;
    private final WfTemplateNodeRepository wfTemplateNodeRepository;
    private final SubmissionOptionService submissionOptionService;

    public SubmissionDetailVO current(CurrentUser currentUser) {
        Long enterpriseId = requireEnterpriseId(currentUser);
        return submissionFormRepository
            .findTopByEnterpriseIdOrderByUpdatedAtDesc(enterpriseId)
            .map(form -> detail(form, currentUser))
            .orElseGet(() -> buildInitialDetailFromEnterprise(enterpriseId));
    }

    @Transactional
    public SubmissionDetailVO saveDraft(SubmissionSaveRequest req, CurrentUser currentUser) {
        Long enterpriseId = requireEnterpriseId(currentUser);
        SubmissionForm form;

        if (req.getSubmissionId() != null) {
            form = submissionFormRepository.findById(req.getSubmissionId()).orElseThrow(() -> new BizException("填报单不存在"));
            assertOwner(form, enterpriseId);
            assertLatestSubmission(form, enterpriseId);
            if (!isEditableStatus(form.getStatus())) {
                throw new BizException("当前状态不允许编辑");
            }
        } else {
            form = submissionFormRepository
                .findTopByEnterpriseIdOrderByUpdatedAtDesc(enterpriseId)
                .orElseGet(() -> createDraftFromEnterprise(enterpriseId, currentUser.getUserId()));
        }

        if (form.getStatus() == SubmissionStatus.RETURNED || form.getStatus() == SubmissionStatus.REJECTED) {
            form.setStatus(SubmissionStatus.DRAFT);
            form.setCurrentNodeSeq(null);
            form.setCurrentNodeName(null);
            form.setLastActionAt(LocalDateTime.now());
        }
        if (req.getReportYear() != null) {
            form.setReportYear(req.getReportYear());
        }
        submissionFormRepository.save(form);

        saveSubmissionContent(form, req);

        auditService.log(
            currentUser.getUserId(),
            "SUBMISSION",
            "SAVE_DRAFT",
            String.valueOf(form.getId()),
            "保存草稿，单据号: " + resolveDocumentNo(form)
        );
        return detail(form, currentUser);
    }

    @Transactional
    public SubmissionDetailVO saveByApprover(Long submissionId, SubmissionSaveRequest req, CurrentUser currentUser) {
        SubmissionForm form = submissionFormRepository.findById(submissionId).orElseThrow(() -> new BizException("填报单不存在"));
        if (req.getSubmissionId() != null && !submissionId.equals(req.getSubmissionId())) {
            throw new BizException("提交单据不匹配");
        }
        if (!isApproverEditableStatus(form.getStatus())) {
            throw new BizException("当前状态不允许管理员编辑");
        }
        if (req.getReportYear() != null) {
            form.setReportYear(req.getReportYear());
        }
        submissionFormRepository.save(form);
        saveSubmissionContent(form, req);
        auditService.log(
            currentUser.getUserId(),
            "SUBMISSION",
            "ADMIN_SAVE",
            String.valueOf(form.getId()),
            "管理员保存修改，单据号: " + resolveDocumentNo(form)
        );
        return detail(form, currentUser);
    }

    @Transactional
    public SubmissionDetailVO submit(Long submissionId, CurrentUser currentUser) {
        Long enterpriseId = requireEnterpriseId(currentUser);
        SubmissionForm form = submissionFormRepository.findById(submissionId).orElseThrow(() -> new BizException("填报单不存在"));
        assertOwner(form, enterpriseId);
        assertLatestSubmission(form, enterpriseId);
        if (!isEditableStatus(form.getStatus())) {
            throw new BizException("当前状态不允许提交");
        }
        SubmissionSaveRequest.DeviceInfo deviceInfo = submissionDeviceInfoRepository.findBySubmissionId(form.getId())
            .map(this::toDeviceInfo)
            .orElse(new SubmissionSaveRequest.DeviceInfo());
        SubmissionSaveRequest.DigitalInfo digitalInfo = submissionDigitalInfoRepository.findBySubmissionId(form.getId())
            .map(this::toDigitalInfo)
            .orElse(new SubmissionSaveRequest.DigitalInfo());
        SubmissionSaveRequest.RdToolInfo rdToolInfo = submissionRdToolInfoRepository.findBySubmissionId(form.getId())
            .map(this::toRdInfo)
            .orElse(new SubmissionSaveRequest.RdToolInfo());
        SubmissionSaveRequest.BasicInfo basicInfo = submissionBasicInfoRepository.findBySubmissionId(form.getId())
            .map(this::toBasicInfo)
            .orElse(new SubmissionSaveRequest.BasicInfo());
        validateSubmissionBeforeSubmit(form.getId(), basicInfo, deviceInfo, digitalInfo, rdToolInfo);

        form.setStatus(SubmissionStatus.SUBMITTED);
        form.setSubmittedBy(currentUser.getUserId());
        form.setSubmittedAt(LocalDateTime.now());
        form.setLastActionAt(LocalDateTime.now());
        submissionFormRepository.save(form);

        auditService.log(
            currentUser.getUserId(),
            "SUBMISSION",
            "SUBMIT",
            String.valueOf(form.getId()),
            "提交审批，单据号: " + resolveDocumentNo(form)
        );
        return detail(form, currentUser);
    }

    public List<SubmissionListItemVO> myList(CurrentUser currentUser) {
        Long enterpriseId = requireEnterpriseId(currentUser);
        return submissionFormRepository.findTopByEnterpriseIdOrderByUpdatedAtDesc(enterpriseId)
            .map(form -> List.of(toListItem(form)))
            .orElseGet(List::of);
    }

    public SubmissionDetailVO getById(Long id, CurrentUser currentUser) {
        SubmissionForm form = submissionFormRepository.findById(id).orElseThrow(() -> new BizException("填报单不存在"));
        if (currentUser.getRoles().contains("ENTERPRISE_USER")) {
            assertOwner(form, requireEnterpriseId(currentUser));
        }
        return detail(form, currentUser);
    }

    public SubmissionForm getByIdOrThrow(Long id) {
        return submissionFormRepository.findById(id).orElseThrow(() -> new BizException("填报单不存在"));
    }

    public SubmissionDetailVO getDetailForExport(Long submissionId) {
        return detail(getByIdOrThrow(submissionId), null);
    }

    public String getDocumentNo(Long submissionId) {
        return resolveDocumentNo(getByIdOrThrow(submissionId));
    }

    @Transactional
    public void updateReviewNode(Long submissionId, SubmissionStatus status, Integer nodeSeq, String nodeName) {
        SubmissionForm form = getByIdOrThrow(submissionId);
        form.setStatus(status);
        form.setCurrentNodeSeq(nodeSeq);
        form.setCurrentNodeName(nodeName);
        form.setLastActionAt(LocalDateTime.now());
        submissionFormRepository.save(form);
    }

    public SubmissionDetailVO detail(SubmissionForm form, CurrentUser currentUser) {
        SubmissionSaveRequest.BasicInfo basic = submissionBasicInfoRepository.findBySubmissionId(form.getId())
            .map(this::toBasicInfo)
            .orElse(null);
        SubmissionSaveRequest.DeviceInfo device = submissionDeviceInfoRepository.findBySubmissionId(form.getId())
            .map(this::toDeviceInfo)
            .orElse(null);
        SubmissionSaveRequest.DigitalInfo digital = submissionDigitalInfoRepository.findBySubmissionId(form.getId())
            .map(this::toDigitalInfo)
            .orElse(null);
        SubmissionSaveRequest.RdToolInfo rd = submissionRdToolInfoRepository.findBySubmissionId(form.getId())
            .map(this::toRdInfo)
            .orElse(null);

        List<SubmissionAttachmentVO> attachments = submissionAttachmentRepository.findBySubmissionIdOrderByUploadedAtDesc(form.getId())
            .stream()
            .map(a -> SubmissionAttachmentVO.builder()
                .id(a.getId())
                .attachmentType(a.getAttachmentType())
                .originalFileName(a.getOriginalFileName())
                .contentType(a.getContentType())
                .uploadedAt(a.getUploadedAt())
                .build())
            .toList();
        ReviewDetailSnapshot reviewDetail = buildReviewDetailSnapshot(form);

        return SubmissionDetailVO.builder()
            .submissionId(form.getId())
            .documentNo(resolveDocumentNo(form))
            .enterpriseId(form.getEnterpriseId())
            .reportYear(form.getReportYear())
            .status(form.getStatus().name())
            .currentNodeSeq(form.getCurrentNodeSeq())
            .currentNodeName(form.getCurrentNodeName())
            .reviewActionLabel(reviewDetail.actionLabel)
            .reviewComment(reviewDetail.comment)
            .reviewHandledAt(reviewDetail.handledAt)
            .submittedAt(form.getSubmittedAt())
            .updatedAt(form.getUpdatedAt())
            .basicInfo(basic)
            .deviceInfo(device)
            .digitalInfo(digital)
            .rdToolInfo(rd)
            .attachments(attachments)
            .build();
    }

    public SubmissionListItemVO toListItem(SubmissionForm form) {
        ReviewSnapshot review = buildReviewSnapshot(form);
        return SubmissionListItemVO.builder()
            .submissionId(form.getId())
            .documentNo(resolveDocumentNo(form))
            .reportYear(form.getReportYear())
            .status(form.getStatus().name())
            .statusLabel(toStatusLabel(form.getStatus()))
            .currentNodeName(form.getCurrentNodeName())
            .progressText(review.progressText)
            .reviewActionLabel(review.actionLabel)
            .reviewComment(review.comment)
            .submittedAt(form.getSubmittedAt())
            .updatedAt(form.getUpdatedAt())
            .build();
    }

    @Transactional
    public void attachFile(Long submissionId, String attachmentType, String storagePath, String originalName, String contentType, Long uploaderId) {
        SubmissionAttachment a = new SubmissionAttachment();
        a.setSubmissionId(submissionId);
        a.setAttachmentType(attachmentType);
        a.setFilePath(storagePath);
        a.setOriginalFileName(originalName);
        a.setContentType(contentType);
        a.setUploaderUserId(uploaderId);
        submissionAttachmentRepository.save(a);
    }

    public boolean isEditableStatus(SubmissionStatus status) {
        return status == SubmissionStatus.DRAFT
            || status == SubmissionStatus.RETURNED
            || status == SubmissionStatus.REJECTED;
    }

    public boolean isApproverEditableStatus(SubmissionStatus status) {
        return status == SubmissionStatus.APPROVED
            || status == SubmissionStatus.RETURNED
            || status == SubmissionStatus.REJECTED
            || status == SubmissionStatus.SUBMITTED
            || status == SubmissionStatus.UNDER_REVIEW;
    }

    public void validateSubmissionBeforeApprove(Long submissionId) {
        SubmissionSaveRequest.DeviceInfo deviceInfo = submissionDeviceInfoRepository.findBySubmissionId(submissionId)
            .map(this::toDeviceInfo)
            .orElse(new SubmissionSaveRequest.DeviceInfo());
        SubmissionSaveRequest.DigitalInfo digitalInfo = submissionDigitalInfoRepository.findBySubmissionId(submissionId)
            .map(this::toDigitalInfo)
            .orElse(new SubmissionSaveRequest.DigitalInfo());
        SubmissionSaveRequest.RdToolInfo rdToolInfo = submissionRdToolInfoRepository.findBySubmissionId(submissionId)
            .map(this::toRdInfo)
            .orElse(new SubmissionSaveRequest.RdToolInfo());
        SubmissionSaveRequest.BasicInfo basicInfo = submissionBasicInfoRepository.findBySubmissionId(submissionId)
            .map(this::toBasicInfo)
            .orElse(new SubmissionSaveRequest.BasicInfo());
        validateSubmissionBeforeSubmit(submissionId, basicInfo, deviceInfo, digitalInfo, rdToolInfo);
    }

    private SubmissionForm createDraftFromEnterprise(Long enterpriseId, Long userId) {
        EnterpriseProfile enterprise = enterpriseService.findByIdOrThrow(enterpriseId);
        EnterpriseService.SurveyIndustryInfo surveyIndustryInfo = enterpriseService.findSurveyIndustryInfo(enterprise.getEnterpriseName());
        String industryCode = surveyIndustryInfo != null && StringUtils.hasText(surveyIndustryInfo.industryCode())
            ? surveyIndustryInfo.industryCode()
            : enterprise.getIndustryCode();
        String industryName = surveyIndustryInfo != null && StringUtils.hasText(surveyIndustryInfo.industryName())
            ? surveyIndustryInfo.industryName()
            : enterprise.getIndustryName();

        SubmissionForm form = new SubmissionForm();
        form.setEnterpriseId(enterpriseId);
        form.setReportYear(LocalDate.now().getYear());
        form.setStatus(SubmissionStatus.DRAFT);
        form.setCreatedBy(userId);
        submissionFormRepository.save(form);
        form.setDocumentNo(generateUniqueDocumentNo(enterprise.getEnterpriseName(), form.getId()));
        submissionFormRepository.save(form);

        SubmissionBasicInfo basic = new SubmissionBasicInfo();
        basic.setSubmissionId(form.getId());
        basic.setEnterpriseName(enterprise.getEnterpriseName());
        basic.setCreditCode(enterprise.getCreditCode());
        basic.setUnitNature(enterprise.getUnitNature());
        basic.setAddress(enterprise.getAddress());
        basic.setEstablishedAt(enterprise.getEstablishedAt());
        basic.setRegisterCapital(enterprise.getRegisterCapital());
        basic.setLegalPerson(enterprise.getLegalPerson());
        basic.setMainProduct(enterprise.getMainProduct());
        basic.setEmployeeCount(enterprise.getEmployeeCount());
        basic.setIndustryCode(industryCode);
        basic.setIndustryName(industryName);
        basic.setAnnualRevenue2023(enterprise.getAnnualRevenue2023());
        basic.setAnnualRevenue2024(enterprise.getAnnualRevenue2024());
        basic.setAnnualRevenue2025(enterprise.getAnnualRevenue2025());
        submissionBasicInfoRepository.save(basic);

        return form;
    }

    private SubmissionDetailVO buildInitialDetailFromEnterprise(Long enterpriseId) {
        EnterpriseProfile enterprise = enterpriseService.findByIdOrThrow(enterpriseId);
        EnterpriseService.SurveyIndustryInfo surveyIndustryInfo = enterpriseService.findSurveyIndustryInfo(enterprise.getEnterpriseName());
        String industryCode = surveyIndustryInfo != null && StringUtils.hasText(surveyIndustryInfo.industryCode())
            ? surveyIndustryInfo.industryCode()
            : enterprise.getIndustryCode();
        String industryName = surveyIndustryInfo != null && StringUtils.hasText(surveyIndustryInfo.industryName())
            ? surveyIndustryInfo.industryName()
            : enterprise.getIndustryName();
        SubmissionSaveRequest.BasicInfo basic = new SubmissionSaveRequest.BasicInfo();
        basic.setEnterpriseName(enterprise.getEnterpriseName());
        basic.setCreditCode(enterprise.getCreditCode());
        basic.setUnitNature(enterprise.getUnitNature());
        basic.setAddress(enterprise.getAddress());
        basic.setEstablishedAt(enterprise.getEstablishedAt());
        basic.setRegisterCapital(enterprise.getRegisterCapital());
        basic.setLegalPerson(enterprise.getLegalPerson());
        basic.setMainProduct(enterprise.getMainProduct());
        basic.setEmployeeCount(enterprise.getEmployeeCount());
        basic.setIndustryCode(industryCode);
        basic.setIndustryName(industryName);
        basic.setAnnualRevenue2023(enterprise.getAnnualRevenue2023());
        basic.setAnnualRevenue2024(enterprise.getAnnualRevenue2024());
        basic.setAnnualRevenue2025(enterprise.getAnnualRevenue2025());

        SubmissionSaveRequest.DeviceInfo deviceInfo = new SubmissionSaveRequest.DeviceInfo();
        deviceInfo.setSelectedProcesses(List.of());
        deviceInfo.setSelectedEquipments(List.of());
        deviceInfo.setInfoDevices(List.of());
        SubmissionSaveRequest.DigitalInfo digitalInfo = new SubmissionSaveRequest.DigitalInfo();
        digitalInfo.setDigitalSystems(List.of());
        SubmissionSaveRequest.RdToolInfo rdToolInfo = new SubmissionSaveRequest.RdToolInfo();
        rdToolInfo.setRdTools(List.of());

        return SubmissionDetailVO.builder()
            .submissionId(null)
            .documentNo(null)
            .enterpriseId(enterpriseId)
            .reportYear(LocalDate.now().getYear())
            .status(SubmissionStatus.DRAFT.name())
            .basicInfo(basic)
            .deviceInfo(deviceInfo)
            .digitalInfo(digitalInfo)
            .rdToolInfo(rdToolInfo)
            .attachments(List.of())
            .build();
    }

    private void upsertBasicInfo(Long submissionId, SubmissionSaveRequest.BasicInfo req) {
        if (req == null) {
            return;
        }
        SubmissionBasicInfo info = submissionBasicInfoRepository.findBySubmissionId(submissionId).orElseGet(SubmissionBasicInfo::new);
        info.setSubmissionId(submissionId);
        info.setEnterpriseName(req.getEnterpriseName());
        info.setCreditCode(req.getCreditCode());
        info.setUnitNature(req.getUnitNature());
        info.setAddress(req.getAddress());
        info.setEstablishedAt(req.getEstablishedAt());
        info.setRegisterCapital(req.getRegisterCapital());
        info.setLegalPerson(req.getLegalPerson());
        info.setMainProduct(req.getMainProduct());
        info.setEmployeeCount(req.getEmployeeCount());
        info.setIndustryCode(req.getIndustryCode());
        info.setIndustryName(req.getIndustryName());
        info.setAnnualRevenue2023(req.getAnnualRevenue2023());
        info.setAnnualRevenue2024(req.getAnnualRevenue2024());
        info.setAnnualRevenue2025(req.getAnnualRevenue2025());
        submissionBasicInfoRepository.save(info);
    }

    private String resolveDocumentNo(SubmissionForm form) {
        if (StringUtils.hasText(form.getDocumentNo())) {
            return form.getDocumentNo();
        }
        SubmissionBasicInfo basicInfo = submissionBasicInfoRepository.findBySubmissionId(form.getId()).orElse(null);
        String enterpriseName = basicInfo == null ? null : basicInfo.getEnterpriseName();
        return generateUniqueDocumentNo(enterpriseName, form.getId());
    }

    private String generateUniqueDocumentNo(String enterpriseName, Long submissionId) {
        for (int salt = 0; salt < 100; salt++) {
            String candidate = SubmissionNoUtils.buildNo(enterpriseName, submissionId, salt);
            if (!submissionFormRepository.existsByDocumentNo(candidate)) {
                return candidate;
            }
        }
        throw new BizException("生成单据号失败，请重试");
    }

    private void upsertDeviceInfo(Long submissionId, SubmissionSaveRequest.DeviceInfo req) {
        if (req == null) {
            return;
        }
        SubmissionDeviceInfo info = submissionDeviceInfoRepository.findBySubmissionId(submissionId).orElseGet(SubmissionDeviceInfo::new);
        info.setSubmissionId(submissionId);
        info.setSelectedProcessesJson(jsonUtils.toJson(req.getSelectedProcesses()));
        info.setSelectedEquipmentsJson(jsonUtils.toJson(req.getSelectedEquipments()));
        info.setInfoDevicesJson(jsonUtils.toJson(req.getInfoDevices()));
        info.setOtherProcess(req.getOtherProcess());
        info.setOtherEquipment(req.getOtherEquipment());
        info.setOtherInfoDevice(req.getOtherInfoDevice());
        submissionDeviceInfoRepository.save(info);
    }

    private void upsertDigitalInfo(Long submissionId, SubmissionSaveRequest.DigitalInfo req) {
        if (req == null) {
            return;
        }
        SubmissionDigitalInfo info = submissionDigitalInfoRepository.findBySubmissionId(submissionId).orElseGet(SubmissionDigitalInfo::new);
        info.setSubmissionId(submissionId);
        info.setDigitalSystemsJson(jsonUtils.toJson(req.getDigitalSystems()));
        info.setOtherSystem(req.getOtherSystem());
        submissionDigitalInfoRepository.save(info);
    }

    private void upsertRdInfo(Long submissionId, SubmissionSaveRequest.RdToolInfo req) {
        if (req == null) {
            return;
        }
        SubmissionRdToolInfo info = submissionRdToolInfoRepository.findBySubmissionId(submissionId).orElseGet(SubmissionRdToolInfo::new);
        info.setSubmissionId(submissionId);
        info.setRdToolsJson(jsonUtils.toJson(req.getRdTools()));
        info.setOtherTool(req.getOtherTool());
        submissionRdToolInfoRepository.save(info);
    }

    private SubmissionSaveRequest.BasicInfo toBasicInfo(SubmissionBasicInfo i) {
        SubmissionSaveRequest.BasicInfo b = new SubmissionSaveRequest.BasicInfo();
        b.setEnterpriseName(i.getEnterpriseName());
        b.setCreditCode(i.getCreditCode());
        b.setUnitNature(i.getUnitNature());
        b.setAddress(i.getAddress());
        b.setEstablishedAt(i.getEstablishedAt());
        b.setRegisterCapital(i.getRegisterCapital());
        b.setLegalPerson(i.getLegalPerson());
        b.setMainProduct(i.getMainProduct());
        b.setEmployeeCount(i.getEmployeeCount());
        b.setIndustryCode(i.getIndustryCode());
        b.setIndustryName(i.getIndustryName());
        b.setAnnualRevenue2023(i.getAnnualRevenue2023());
        b.setAnnualRevenue2024(i.getAnnualRevenue2024());
        b.setAnnualRevenue2025(i.getAnnualRevenue2025());
        return b;
    }

    private SubmissionSaveRequest.DeviceInfo toDeviceInfo(SubmissionDeviceInfo i) {
        SubmissionSaveRequest.DeviceInfo d = new SubmissionSaveRequest.DeviceInfo();
        d.setSelectedProcesses(jsonUtils.toStringList(i.getSelectedProcessesJson()));
        d.setSelectedEquipments(jsonUtils.toStringList(i.getSelectedEquipmentsJson()));
        d.setInfoDevices(jsonUtils.toStringList(i.getInfoDevicesJson()));
        d.setOtherProcess(i.getOtherProcess());
        d.setOtherEquipment(i.getOtherEquipment());
        d.setOtherInfoDevice(i.getOtherInfoDevice());
        return d;
    }

    private SubmissionSaveRequest.DigitalInfo toDigitalInfo(SubmissionDigitalInfo i) {
        SubmissionSaveRequest.DigitalInfo d = new SubmissionSaveRequest.DigitalInfo();
        d.setDigitalSystems(jsonUtils.toStringList(i.getDigitalSystemsJson()));
        d.setOtherSystem(i.getOtherSystem());
        return d;
    }

    private SubmissionSaveRequest.RdToolInfo toRdInfo(SubmissionRdToolInfo i) {
        SubmissionSaveRequest.RdToolInfo d = new SubmissionSaveRequest.RdToolInfo();
        d.setRdTools(jsonUtils.toStringList(i.getRdToolsJson()));
        d.setOtherTool(i.getOtherTool());
        return d;
    }

    private ReviewSnapshot buildReviewSnapshot(SubmissionForm form) {
        if (form.getStatus() == SubmissionStatus.DRAFT) {
            return new ReviewSnapshot("草稿（未提交）", null, null);
        }

        WfInstance instance = wfInstanceRepository.findByBusinessTypeAndBusinessId(WorkflowService.BIZ_TYPE_SUBMISSION, form.getId())
            .orElse(null);
        if (instance == null) {
            if (form.getStatus() == SubmissionStatus.APPROVED) {
                return new ReviewSnapshot("审批通过", null, null);
            }
            if (form.getStatus() == SubmissionStatus.REJECTED || form.getStatus() == SubmissionStatus.RETURNED) {
                return new ReviewSnapshot("流程结束", null, null);
            }
            return new ReviewSnapshot("待发起流程", null, null);
        }

        List<WfTask> tasks = wfTaskRepository.findByInstanceIdOrderByNodeSeqAsc(instance.getId());
        int totalNodes = wfTemplateNodeRepository.findByTemplateIdOrderByNodeSeqAsc(instance.getTemplateId()).size();
        if (totalNodes <= 0) {
            totalNodes = 1;
        }
        int doneNodes = (int) tasks.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();

        String progressText;
        if (form.getStatus() == SubmissionStatus.APPROVED) {
            progressText = "审批通过（" + totalNodes + "/" + totalNodes + "）";
        } else if (form.getStatus() == SubmissionStatus.REJECTED || form.getStatus() == SubmissionStatus.RETURNED) {
            progressText = "流程结束（" + Math.min(doneNodes, totalNodes) + "/" + totalNodes + "）";
        } else {
            int current = instance.getCurrentNodeSeq() == null ? Math.min(doneNodes + 1, totalNodes) : instance.getCurrentNodeSeq();
            progressText = "审批中（第" + current + "/" + totalNodes + "节点）";
        }

        WfTask latestDone = tasks.stream()
            .filter(t -> t.getStatus() == TaskStatus.DONE)
            .max(Comparator.comparing(WfTask::getHandledAt, Comparator.nullsLast(Comparator.naturalOrder())))
            .orElse(null);

        if (latestDone == null || latestDone.getAction() == null) {
            return new ReviewSnapshot(progressText, null, null);
        }

        String actionLabel = switch (latestDone.getAction()) {
            case APPROVE -> "通过";
            case REJECT -> "驳回";
            case RETURN -> "退回修改";
        };

        String comment = StringUtils.hasText(latestDone.getCommentText()) ? latestDone.getCommentText() : null;
        if (latestDone.getAction() != TaskAction.REJECT && latestDone.getAction() != TaskAction.RETURN) {
            return new ReviewSnapshot(progressText, null, null);
        }
        return new ReviewSnapshot(progressText, actionLabel, comment);
    }

    private ReviewDetailSnapshot buildReviewDetailSnapshot(SubmissionForm form) {
        WfInstance instance = wfInstanceRepository.findByBusinessTypeAndBusinessId(WorkflowService.BIZ_TYPE_SUBMISSION, form.getId())
            .orElse(null);
        if (instance == null) {
            if (form.getStatus() == SubmissionStatus.APPROVED) {
                return new ReviewDetailSnapshot("通过", null, form.getLastActionAt());
            }
            if (form.getStatus() == SubmissionStatus.REJECTED) {
                return new ReviewDetailSnapshot("驳回", null, form.getLastActionAt());
            }
            if (form.getStatus() == SubmissionStatus.RETURNED) {
                return new ReviewDetailSnapshot("退回修改", null, form.getLastActionAt());
            }
            return new ReviewDetailSnapshot(null, null, null);
        }

        List<WfTask> tasks = wfTaskRepository.findByInstanceIdOrderByNodeSeqAsc(instance.getId());
        WfTask latestDone = tasks.stream()
            .filter(t -> t.getStatus() == TaskStatus.DONE)
            .max(Comparator.comparing(WfTask::getHandledAt, Comparator.nullsLast(Comparator.naturalOrder())))
            .orElse(null);
        if (latestDone == null || latestDone.getAction() == null) {
            return new ReviewDetailSnapshot(null, null, null);
        }
        String actionLabel = taskActionLabel(latestDone.getAction());
        String comment = StringUtils.hasText(latestDone.getCommentText()) ? latestDone.getCommentText() : null;
        LocalDateTime handledAt = latestDone.getHandledAt() != null ? latestDone.getHandledAt() : form.getLastActionAt();
        return new ReviewDetailSnapshot(actionLabel, comment, handledAt);
    }

    private String taskActionLabel(TaskAction action) {
        if (action == null) {
            return null;
        }
        return switch (action) {
            case APPROVE -> "通过";
            case REJECT -> "驳回";
            case RETURN -> "退回修改";
        };
    }

    private String toStatusLabel(SubmissionStatus status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case DRAFT -> "草稿";
            case SUBMITTED, UNDER_REVIEW -> "审批中";
            case APPROVED -> "已通过";
            case REJECTED -> "已驳回";
            case RETURNED -> "已退回";
        };
    }

    private void assertOwner(SubmissionForm form, Long enterpriseId) {
        if (!form.getEnterpriseId().equals(enterpriseId)) {
            throw new BizException("无权访问该填报单");
        }
    }

    private void assertLatestSubmission(SubmissionForm form, Long enterpriseId) {
        SubmissionForm latest = submissionFormRepository.findTopByEnterpriseIdOrderByUpdatedAtDesc(enterpriseId)
            .orElseThrow(() -> new BizException("填报单不存在"));
        if (!latest.getId().equals(form.getId())) {
            throw new BizException("当前仅允许修改企业最新的一份填报");
        }
    }

    private void saveSubmissionContent(SubmissionForm form, SubmissionSaveRequest req) {
        validateRequiredBasicInfo(req.getBasicInfo());
        validateOtherFields(req.getDeviceInfo(), req.getDigitalInfo(), req.getRdToolInfo());
        upsertBasicInfo(form.getId(), req.getBasicInfo());
        upsertDeviceInfo(form.getId(), req.getDeviceInfo());
        upsertDigitalInfo(form.getId(), req.getDigitalInfo());
        upsertRdInfo(form.getId(), req.getRdToolInfo());
    }

    private void validateSubmissionBeforeSubmit(Long submissionId,
                                                SubmissionSaveRequest.BasicInfo basicInfo,
                                                SubmissionSaveRequest.DeviceInfo deviceInfo,
                                                SubmissionSaveRequest.DigitalInfo digitalInfo,
                                                SubmissionSaveRequest.RdToolInfo rdToolInfo) {
        validateRequiredBasicInfo(basicInfo);
        validateRequiredSelections(deviceInfo, digitalInfo, rdToolInfo);
        validateOtherFields(deviceInfo, digitalInfo, rdToolInfo);
        validateRequiredAttachments(submissionId);
    }

    private void validateOtherFields(SubmissionSaveRequest.DeviceInfo device,
                                     SubmissionSaveRequest.DigitalInfo digital,
                                     SubmissionSaveRequest.RdToolInfo rdTool) {
        String digitalOtherOption = submissionOptionService.getDigitalOtherOptionName();
        String rdOtherOption = submissionOptionService.getRdToolOtherOptionName();
        if (containsOption(device == null ? null : device.getSelectedProcesses(), OTHER_OPTION)
            && !StringUtils.hasText(device.getOtherProcess())) {
            throw new BizException("已勾选主要工序“其他”，请填写具体内容");
        }
        if (containsOption(device == null ? null : device.getSelectedEquipments(), OTHER_OPTION)
            && !StringUtils.hasText(device.getOtherEquipment())) {
            throw new BizException("已勾选主要设备“其他”，请填写具体内容");
        }
        if (StringUtils.hasText(digitalOtherOption)
            && containsOption(digital == null ? null : digital.getDigitalSystems(), digitalOtherOption)
            && !StringUtils.hasText(digital.getOtherSystem())) {
            throw new BizException("已勾选数字化系统“其他”，请填写具体内容");
        }
        if (StringUtils.hasText(rdOtherOption)
            && containsOption(rdTool == null ? null : rdTool.getRdTools(), rdOtherOption)
            && !StringUtils.hasText(rdTool.getOtherTool())) {
            throw new BizException("已勾选研发工具“其他研发设计类软件”，请填写具体内容");
        }
    }

    private void validateRequiredBasicInfo(SubmissionSaveRequest.BasicInfo basicInfo) {
        List<String> missingFields = new ArrayList<>();
        if (!StringUtils.hasText(basicInfo == null ? null : basicInfo.getEnterpriseName())) {
            missingFields.add("企业名称");
        }
        if (!StringUtils.hasText(basicInfo == null ? null : basicInfo.getIndustryCode())) {
            missingFields.add("所属行业代码");
        }
        if (!missingFields.isEmpty()) {
            throw new BizException("请先填写必填项：" + String.join("、", missingFields));
        }
    }

    private boolean containsOption(List<String> options, String expected) {
        if (options == null || options.isEmpty()) {
            return false;
        }
        return options.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .anyMatch(expected::equals);
    }

    private void validateRequiredSelections(SubmissionSaveRequest.DeviceInfo device,
                                            SubmissionSaveRequest.DigitalInfo digital,
                                            SubmissionSaveRequest.RdToolInfo rdTool) {
        List<String> missingFields = new ArrayList<>();
        if (!hasAnyOption(device == null ? null : device.getSelectedProcesses())) {
            missingFields.add("主要工序");
        }
        if (!hasAnyOption(device == null ? null : device.getSelectedEquipments())) {
            missingFields.add("主要设备");
        }
        if (!hasAnyOption(digital == null ? null : digital.getDigitalSystems())) {
            missingFields.add("系统投入");
        }
        if (!hasAnyOption(rdTool == null ? null : rdTool.getRdTools())) {
            missingFields.add("工具投入");
        }
        if (!missingFields.isEmpty()) {
            throw new BizException("请先填写必填项：" + String.join("、", missingFields));
        }
    }

    private boolean hasAnyOption(List<String> options) {
        if (options == null || options.isEmpty()) {
            return false;
        }
        return options.stream().anyMatch(StringUtils::hasText);
    }

    private void validateRequiredAttachments(Long submissionId) {
        List<String> missingAttachments = new ArrayList<>();
        List<String> deviceTypes = List.of(ATTACHMENT_DEVICE, ATTACHMENT_LEGACY);
        long deviceCount = submissionAttachmentRepository.countBySubmissionIdAndAttachmentTypeIn(
            submissionId,
            deviceTypes
        );
        if (deviceCount <= 0) {
            missingAttachments.add("技改设备投入材料");
        } else {
            List<SubmissionAttachment> deviceAttachments = submissionAttachmentRepository
                .findBySubmissionIdAndAttachmentTypeIn(submissionId, deviceTypes);
            boolean hasPdf = deviceAttachments.stream().anyMatch(this::isPdfAttachment);
            boolean hasExcel = deviceAttachments.stream().anyMatch(this::isExcelAttachment);
            if (!hasExcel || !hasPdf) {
                missingAttachments.add("技改设备投入材料（需至少1份Excel和1份PDF）");
            }
        }
        if (submissionAttachmentRepository.countBySubmissionIdAndAttachmentType(submissionId, ATTACHMENT_DIGITAL) <= 0) {
            missingAttachments.add("数字化系统材料");
        }
        if (submissionAttachmentRepository.countBySubmissionIdAndAttachmentType(submissionId, ATTACHMENT_RD_TOOL) <= 0) {
            missingAttachments.add("研发工具材料");
        }

        if (!missingAttachments.isEmpty()) {
            throw new BizException("请先上传必填材料：" + String.join("、", missingAttachments));
        }
    }

    private Long requireEnterpriseId(CurrentUser currentUser) {
        if (currentUser.getEnterpriseId() == null) {
            throw new BizException("当前账号未绑定企业");
        }
        return currentUser.getEnterpriseId();
    }

    private boolean isPdfAttachment(SubmissionAttachment attachment) {
        String ext = extOf(attachment.getOriginalFileName());
        if ("pdf".equals(ext)) {
            return true;
        }
        String contentType = attachment.getContentType();
        return contentType != null && contentType.toLowerCase().contains("pdf");
    }

    private boolean isExcelAttachment(SubmissionAttachment attachment) {
        String ext = extOf(attachment.getOriginalFileName());
        if ("xls".equals(ext) || "xlsx".equals(ext)) {
            return true;
        }
        String contentType = attachment.getContentType() == null ? "" : attachment.getContentType().toLowerCase();
        return contentType.contains("spreadsheet")
            || contentType.contains("ms-excel")
            || contentType.contains("officedocument.spreadsheetml");
    }

    private String extOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int idx = fileName.lastIndexOf('.');
        if (idx < 0 || idx == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(idx + 1).toLowerCase();
    }

    private record ReviewSnapshot(String progressText, String actionLabel, String comment) {
    }

    private record ReviewDetailSnapshot(String actionLabel, String comment, LocalDateTime handledAt) {
    }
}
