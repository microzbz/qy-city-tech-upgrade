package com.qy.citytechupgrade.approval;

import com.qy.citytechupgrade.audit.AuditService;
import com.qy.citytechupgrade.common.dto.PagedResult;
import com.qy.citytechupgrade.common.enums.SubmissionStatus;
import com.qy.citytechupgrade.common.enums.TaskAction;
import com.qy.citytechupgrade.common.enums.TaskStatus;
import com.qy.citytechupgrade.common.enums.WorkflowStatus;
import com.qy.citytechupgrade.common.exception.BizException;
import com.qy.citytechupgrade.common.security.CurrentUser;
import com.qy.citytechupgrade.common.util.SubmissionNoUtils;
import com.qy.citytechupgrade.notification.MsgCenterService;
import com.qy.citytechupgrade.notification.NoticeService;
import com.qy.citytechupgrade.submission.SubmissionBasicInfo;
import com.qy.citytechupgrade.submission.SubmissionBasicInfoRepository;
import com.qy.citytechupgrade.submission.SubmissionDetailVO;
import com.qy.citytechupgrade.submission.SubmissionForm;
import com.qy.citytechupgrade.submission.SubmissionFormRepository;
import com.qy.citytechupgrade.submission.SubmissionSaveRequest;
import com.qy.citytechupgrade.submission.SubmissionService;
import com.qy.citytechupgrade.user.SysUser;
import com.qy.citytechupgrade.user.UserService;
import com.qy.citytechupgrade.workflow.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApprovalService {
    private final WfInstanceRepository wfInstanceRepository;
    private final WfTaskRepository wfTaskRepository;
    private final WorkflowService workflowService;
    private final SubmissionService submissionService;
    private final SubmissionFormRepository submissionFormRepository;
    private final SubmissionBasicInfoRepository submissionBasicInfoRepository;
    private final NoticeService noticeService;
    private final MsgCenterService msgCenterService;
    private final UserService userService;
    private final AuditService auditService;

    @Transactional
    public void startWorkflowForSubmission(Long submissionId, CurrentUser operator) {
        log.info("[审批] 开始发起流程，submissionId={}，operatorId={}，roles={}",
            submissionId, operator.getUserId(), operator.getRoles());
        SubmissionForm form = submissionService.getByIdOrThrow(submissionId);
        if (form.getStatus() != SubmissionStatus.SUBMITTED) {
            throw new BizException("当前单据不在可提交流程状态");
        }
        String documentNo = resolveDocumentNo(form, submissionId);
        if (!workflowService.isApprovalEnabled(WorkflowService.BIZ_TYPE_SUBMISSION)) {
            submissionService.updateReviewNode(submissionId, SubmissionStatus.APPROVED, null, null);
            notifyEnterpriseUsers(submissionId, "审批结果通知", "单据号 " + documentNo + " 已自动通过");
            notifyEnterpriseContact(submissionId, SubmissionStatus.APPROVED, "自动通过", null);
            auditService.log(operator.getUserId(), "WORKFLOW", "AUTO_APPROVE", String.valueOf(submissionId),
                "审批开关关闭，自动通过，单据号: " + documentNo);
            log.info("[审批] 流程自动通过，submissionId={}，documentNo={}", submissionId, documentNo);
            return;
        }

        WfTemplate template = workflowService.getActiveTemplateOrThrow(WorkflowService.BIZ_TYPE_SUBMISSION);
        List<WfTemplateNode> nodes = workflowService.listTemplateNodes(template.getId());
        WfTemplateNode firstNode = nodes.stream().min(Comparator.comparing(WfTemplateNode::getNodeSeq))
            .orElseThrow(() -> new BizException("流程模板未配置节点"));

        wfInstanceRepository.findByBusinessTypeAndBusinessId(WorkflowService.BIZ_TYPE_SUBMISSION, submissionId)
            .ifPresent(instance -> {
                if (instance.getStatus() == WorkflowStatus.RUNNING) {
                    throw new BizException("当前单据已有审批流程在进行中");
                }
                List<WfTask> oldTasks = wfTaskRepository.findByInstanceIdOrderByNodeSeqAsc(instance.getId());
                wfTaskRepository.deleteAll(oldTasks);
                wfTaskRepository.flush();
                wfInstanceRepository.delete(instance);
                wfInstanceRepository.flush();
            });

        WfInstance instance = new WfInstance();
        instance.setBusinessType(WorkflowService.BIZ_TYPE_SUBMISSION);
        instance.setBusinessId(submissionId);
        instance.setTemplateId(template.getId());
        instance.setStatus(WorkflowStatus.RUNNING);
        instance.setCurrentNodeSeq(firstNode.getNodeSeq());
        instance = wfInstanceRepository.save(instance);

        WfTask task = new WfTask();
        task.setInstanceId(instance.getId());
        task.setNodeSeq(firstNode.getNodeSeq());
        task.setNodeName(firstNode.getNodeName());
        task.setRoleCode(firstNode.getRoleCode());
        task.setStatus(TaskStatus.TODO);
        wfTaskRepository.save(task);

        submissionService.updateReviewNode(submissionId, SubmissionStatus.SUBMITTED, firstNode.getNodeSeq(), firstNode.getNodeName());
        notifyApprovers(firstNode.getRoleCode(), "您有新的待审批单据", "单据号 " + documentNo + " 已进入审批");

        auditService.log(operator.getUserId(), "WORKFLOW", "START", String.valueOf(submissionId), "发起审批流程，单据号: " + documentNo);
        log.info("[审批] 流程已发起，submissionId={}，documentNo={}，instanceId={}，templateId={}，firstTaskId={}，firstNodeSeq={}，firstNodeName={}，roleCode={}",
            submissionId, documentNo, instance.getId(), template.getId(), task.getId(), firstNode.getNodeSeq(), firstNode.getNodeName(), firstNode.getRoleCode());
    }

    public PagedResult<ApprovalTaskVO> todo(
        String documentNo,
        String enterpriseName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer page,
        Integer size,
        CurrentUser currentUser
    ) {
        List<WfTask> tasks;
        if (currentUser.getRoles().contains("SYS_ADMIN")) {
            tasks = wfTaskRepository.findByStatusOrderByUpdatedAtDesc(TaskStatus.TODO);
        } else {
            tasks = wfTaskRepository.findByStatusAndRoleCodeInOrderByCreatedAtDesc(TaskStatus.TODO, currentUser.getRoles().stream().toList());
        }
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            throw new BizException("开始时间不能晚于结束时间");
        }
        String normalizedDocumentNo = normalizeKeyword(documentNo);
        String normalizedEnterpriseName = normalizeKeyword(enterpriseName);
        List<ApprovalTaskVO> filtered = tasks.stream()
            .map(this::toTaskVO)
            .filter(task -> matchesTodoFilters(task, normalizedDocumentNo, normalizedEnterpriseName, startTime, endTime))
            .toList();
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        int fromIndex = Math.min((safePage - 1) * safeSize, filtered.size());
        int toIndex = Math.min(fromIndex + safeSize, filtered.size());
        return PagedResult.of(filtered.subList(fromIndex, toIndex), filtered.size(), safePage, safeSize);
    }

    public List<ApprovalTaskVO> done(CurrentUser currentUser) {
        List<WfTask> tasks;
        if (currentUser.getRoles().contains("SYS_ADMIN")) {
            tasks = wfTaskRepository.findByStatusOrderByUpdatedAtDesc(TaskStatus.DONE);
        } else {
            tasks = wfTaskRepository.findByStatusAndRoleCodeInOrderByCreatedAtDesc(TaskStatus.DONE, currentUser.getRoles().stream().toList());
        }
        return tasks.stream().map(this::toTaskVO).toList();
    }

    public ApprovalTaskVO detail(Long taskId, CurrentUser currentUser) {
        WfTask task = wfTaskRepository.findById(taskId).orElseThrow(() -> new BizException("审批任务不存在"));
        if (!currentUser.getRoles().contains("SYS_ADMIN") && !currentUser.getRoles().contains(task.getRoleCode())) {
            throw new BizException("无权查看该审批任务");
        }
        return toTaskVO(task);
    }

    @Transactional
    public void approve(Long taskId, String comment, CurrentUser operator) {
        handle(taskId, TaskAction.APPROVE, comment, operator);
    }

    @Transactional
    public void reject(Long taskId, String comment, CurrentUser operator) {
        handle(taskId, TaskAction.REJECT, comment, operator);
    }

    @Transactional
    public void returnBack(Long taskId, String comment, CurrentUser operator) {
        handle(taskId, TaskAction.RETURN, comment, operator);
    }

    @Transactional
    public SubmissionDetailVO saveEditedSubmission(Long submissionId, SubmissionSaveRequest request, CurrentUser operator) {
        return submissionService.saveByApprover(submissionId, request, operator);
    }

    @Transactional
    public SubmissionDetailVO submitEditedSubmission(Long submissionId, CurrentUser operator) {
        log.info("[审批] 管理员开始提交修改后的单据，submissionId={}，operatorId={}，roles={}",
            submissionId, operator.getUserId(), operator.getRoles());
        SubmissionForm form = submissionService.getByIdOrThrow(submissionId);
        if (!submissionService.isApproverEditableStatus(form.getStatus())) {
            throw new BizException("当前状态不允许管理员提交修改");
        }
        submissionService.validateSubmissionBeforeApprove(submissionId);

        LocalDateTime now = LocalDateTime.now();
        wfInstanceRepository.findByBusinessTypeAndBusinessId(WorkflowService.BIZ_TYPE_SUBMISSION, submissionId)
            .ifPresent(instance -> finalizeInstanceAfterAdminEdit(instance, operator, now));

        submissionService.updateReviewNode(submissionId, SubmissionStatus.APPROVED, null, null);
        SubmissionForm refreshed = submissionService.getByIdOrThrow(submissionId);
        String documentNo = resolveDocumentNo(refreshed, submissionId);
        notifyEnterpriseUsers(submissionId, "审批结果通知", "单据号 " + documentNo + " 已由管理员修改后确认通过");
        notifyEnterpriseContact(submissionId, SubmissionStatus.APPROVED, "管理员修改后确认通过", null);
        auditService.log(operator.getUserId(), "APPROVAL", "ADMIN_EDIT_APPROVE", String.valueOf(submissionId),
            "管理员修改后提交并确认通过，单据号: " + documentNo);
        log.info("[审批] 管理员提交修改后的单据成功，submissionId={}，documentNo={}，operatorId={}",
            submissionId, documentNo, operator.getUserId());
        return submissionService.detail(refreshed, operator);
    }

    @Transactional
    public SubmissionDetailVO returnApprovedSubmission(Long submissionId, String comment, CurrentUser operator) {
        log.info("[审批] 管理员开始退回已通过单据，submissionId={}，operatorId={}，comment={}",
            submissionId, operator.getUserId(), comment);
        SubmissionForm form = submissionService.getByIdOrThrow(submissionId);
        if (form.getStatus() != SubmissionStatus.APPROVED) {
            throw new BizException("仅已审批通过的填报支持退回企业");
        }

        LocalDateTime now = LocalDateTime.now();
        wfInstanceRepository.findByBusinessTypeAndBusinessId(WorkflowService.BIZ_TYPE_SUBMISSION, submissionId)
            .ifPresent(instance -> appendSyntheticTask(instance, TaskAction.RETURN, comment, operator, now, "管理员退回"));

        submissionService.updateReviewNode(submissionId, SubmissionStatus.RETURNED, null, null);
        SubmissionForm refreshed = submissionService.getByIdOrThrow(submissionId);
        String documentNo = resolveDocumentNo(refreshed, submissionId);
        notifyEnterpriseUsers(
            submissionId,
            "审批结果通知",
            "单据号 " + documentNo + " 已被管理员退回修改" + (StringUtils.hasText(comment) ? "，原因: " + comment : "")
        );
        notifyEnterpriseContact(submissionId, SubmissionStatus.RETURNED, "管理员退回企业", comment);
        auditService.log(operator.getUserId(), "APPROVAL", "ADMIN_RETURN_APPROVED", String.valueOf(submissionId),
            buildAuditDetail(documentNo, "管理员退回企业", comment));
        log.info("[审批] 管理员退回已通过单据成功，submissionId={}，documentNo={}，operatorId={}",
            submissionId, documentNo, operator.getUserId());
        return submissionService.detail(refreshed, operator);
    }

    private void handle(Long taskId, TaskAction action, String comment, CurrentUser operator) {
        log.info("[审批] 开始处理任务，taskId={}，action={}，operatorId={}，roles={}，comment={}",
            taskId, action, operator.getUserId(), operator.getRoles(), comment);
        WfTask task = wfTaskRepository.findById(taskId).orElseThrow(() -> new BizException("审批任务不存在"));
        if (task.getStatus() != TaskStatus.TODO) {
            throw new BizException("该任务已处理");
        }

        boolean hasRole = operator.getRoles().contains(task.getRoleCode());
        if (!hasRole && !operator.getRoles().contains("SYS_ADMIN")) {
            throw new BizException("无权处理该审批任务");
        }

        WfInstance instance = wfInstanceRepository.findById(task.getInstanceId()).orElseThrow(() -> new BizException("流程实例不存在"));
        Long submissionId = instance.getBusinessId();

        task.setStatus(TaskStatus.DONE);
        task.setAction(action);
        task.setCommentText(comment);
        task.setAssigneeUserId(operator.getUserId());
        task.setHandledAt(LocalDateTime.now());
        wfTaskRepository.save(task);
        log.info("[审批] 任务已处理完成，taskId={}，instanceId={}，submissionId={}，action={}，operatorId={}",
            task.getId(), task.getInstanceId(), submissionId, action, operator.getUserId());

        if (action == TaskAction.APPROVE) {
            onApprove(instance, task, submissionId, operator, comment);
        } else if (action == TaskAction.REJECT) {
            onFinish(instance, submissionId, SubmissionStatus.REJECTED, operator, "驳回", comment);
        } else {
            onFinish(instance, submissionId, SubmissionStatus.RETURNED, operator, "退回修改", comment);
        }
    }

    private void onApprove(WfInstance instance, WfTask currentTask, Long submissionId, CurrentUser operator, String comment) {
        List<WfTemplateNode> nodes = workflowService.listTemplateNodes(instance.getTemplateId());
        WfTemplateNode next = nodes.stream()
            .filter(n -> n.getNodeSeq() > currentTask.getNodeSeq())
            .min(Comparator.comparing(WfTemplateNode::getNodeSeq))
            .orElse(null);

        if (next == null) {
            log.info("[审批] 当前任务通过后到达最终节点，taskId={}，submissionId={}，operatorId={}",
                currentTask.getId(), submissionId, operator.getUserId());
            onFinish(instance, submissionId, SubmissionStatus.APPROVED, operator, "审批通过", comment);
            return;
        }

        WfTask nextTask = new WfTask();
        nextTask.setInstanceId(instance.getId());
        nextTask.setNodeSeq(next.getNodeSeq());
        nextTask.setNodeName(next.getNodeName());
        nextTask.setRoleCode(next.getRoleCode());
        nextTask.setStatus(TaskStatus.TODO);
        wfTaskRepository.save(nextTask);

        instance.setStatus(WorkflowStatus.RUNNING);
        instance.setCurrentNodeSeq(next.getNodeSeq());
        wfInstanceRepository.save(instance);

        submissionService.updateReviewNode(submissionId, SubmissionStatus.UNDER_REVIEW, next.getNodeSeq(), next.getNodeName());
        SubmissionForm form = submissionFormRepository.findById(submissionId).orElse(null);
        String documentNo = resolveDocumentNo(form, submissionId);
        notifyApprovers(next.getRoleCode(), "您有新的待审批单据", "单据号 " + documentNo + " 已流转到下一节点");
        notifyEnterpriseUsers(submissionId, "审批进度更新", "单据号 " + documentNo + " 正在审核中，当前节点: " + next.getNodeName());

        auditService.log(operator.getUserId(), "APPROVAL", "APPROVE", String.valueOf(submissionId),
            "节点通过，单据号: " + documentNo);
        log.info("[审批] 任务已流转到下一节点，submissionId={}，documentNo={}，nextTaskId={}，nextNodeSeq={}，nextNodeName={}，nextRoleCode={}",
            submissionId, documentNo, nextTask.getId(), next.getNodeSeq(), next.getNodeName(), next.getRoleCode());
    }

    private void onFinish(WfInstance instance,
                          Long submissionId,
                          SubmissionStatus status,
                          CurrentUser operator,
                          String actionName,
                          String comment) {
        instance.setStatus(WorkflowStatus.FINISHED);
        instance.setCurrentNodeSeq(null);
        instance.setFinishedAt(LocalDateTime.now());
        wfInstanceRepository.save(instance);

        submissionService.updateReviewNode(submissionId, status, null, null);
        SubmissionForm form = submissionFormRepository.findById(submissionId).orElse(null);
        String documentNo = resolveDocumentNo(form, submissionId);
        notifyEnterpriseUsers(submissionId, "审批结果通知",
            "单据号 " + documentNo + " 处理结果: " + actionName + (comment == null || comment.isBlank() ? "" : "，意见: " + comment));
        notifyEnterpriseContact(submissionId, status, actionName, comment);
        auditService.log(operator.getUserId(), "APPROVAL", actionName, String.valueOf(submissionId),
            buildAuditDetail(documentNo, actionName, comment));
        log.info("[审批] 流程处理完成，submissionId={}，documentNo={}，resultStatus={}，actionName={}，operatorId={}，comment={}",
            submissionId, documentNo, status, actionName, operator.getUserId(), comment);
    }

    private String buildAuditDetail(String documentNo, String actionName, String comment) {
        StringBuilder detail = new StringBuilder(actionName).append("，单据号: ").append(documentNo);
        if (StringUtils.hasText(comment)) {
            detail.append("，意见: ").append(comment.trim());
        }
        return detail.toString();
    }

    private void finalizeInstanceAfterAdminEdit(WfInstance instance, CurrentUser operator, LocalDateTime handledAt) {
        List<WfTask> tasks = wfTaskRepository.findByInstanceIdOrderByNodeSeqAsc(instance.getId());
        List<WfTask> todoTasks = tasks.stream()
            .filter(task -> task.getStatus() == TaskStatus.TODO)
            .toList();
        if (todoTasks.isEmpty()) {
            appendSyntheticTask(instance, TaskAction.APPROVE, null, operator, handledAt, "管理员修改确认");
        } else {
            for (WfTask task : todoTasks) {
                task.setStatus(TaskStatus.DONE);
                task.setAction(TaskAction.APPROVE);
                task.setCommentText(null);
                task.setAssigneeUserId(operator.getUserId());
                task.setHandledAt(handledAt);
                wfTaskRepository.save(task);
            }
        }
        instance.setStatus(WorkflowStatus.FINISHED);
        instance.setCurrentNodeSeq(null);
        instance.setFinishedAt(handledAt);
        wfInstanceRepository.save(instance);
    }

    private void appendSyntheticTask(WfInstance instance,
                                     TaskAction action,
                                     String comment,
                                     CurrentUser operator,
                                     LocalDateTime handledAt,
                                     String nodeName) {
        int nextSeq = wfTaskRepository.findByInstanceIdOrderByNodeSeqAsc(instance.getId()).stream()
            .map(WfTask::getNodeSeq)
            .filter(seq -> seq != null)
            .max(Integer::compareTo)
            .orElse(0) + 1;

        WfTask syntheticTask = new WfTask();
        syntheticTask.setInstanceId(instance.getId());
        syntheticTask.setNodeSeq(nextSeq);
        syntheticTask.setNodeName(nodeName);
        syntheticTask.setRoleCode(resolveOperatorRole(operator));
        syntheticTask.setStatus(TaskStatus.DONE);
        syntheticTask.setAssigneeUserId(operator.getUserId());
        syntheticTask.setAction(action);
        syntheticTask.setCommentText(comment);
        syntheticTask.setHandledAt(handledAt);
        wfTaskRepository.save(syntheticTask);

        instance.setStatus(WorkflowStatus.FINISHED);
        instance.setCurrentNodeSeq(null);
        instance.setFinishedAt(handledAt);
        wfInstanceRepository.save(instance);
    }

    private String resolveOperatorRole(CurrentUser operator) {
        if (operator.getRoles().contains("SYS_ADMIN")) {
            return "SYS_ADMIN";
        }
        if (operator.getRoles().contains("APPROVER_ADMIN")) {
            return "APPROVER_ADMIN";
        }
        return operator.getRoles().stream().findFirst().orElse("APPROVER_ADMIN");
    }

    private void notifyApprovers(String roleCode, String title, String content) {
        List<SysUser> users = userService.listUsersByRoleCode(roleCode);
        for (SysUser user : users) {
            noticeService.push(user.getId(), title, content);
        }
    }

    private void notifyEnterpriseContact(Long submissionId, SubmissionStatus status, String actionName, String comment) {
        msgCenterService.sendApprovalResult(submissionId, status, actionName, comment);
    }

    private void notifyEnterpriseUsers(Long submissionId, String title, String content) {
        SubmissionForm form = submissionFormRepository.findById(submissionId).orElse(null);
        if (form == null) {
            return;
        }
        List<SysUser> enterpriseUsers = userService.listUsersByRoleCode("ENTERPRISE_USER").stream()
            .filter(u -> form.getEnterpriseId().equals(u.getEnterpriseId()))
            .toList();
        for (SysUser user : enterpriseUsers) {
            noticeService.push(user.getId(), title, content);
        }
    }

    private ApprovalTaskVO toTaskVO(WfTask task) {
        WfInstance instance = wfInstanceRepository.findById(task.getInstanceId()).orElse(null);
        Long submissionId = instance == null ? null : instance.getBusinessId();
        SubmissionForm form = submissionId == null ? null : submissionFormRepository.findById(submissionId).orElse(null);
        SubmissionBasicInfo basic = submissionId == null ? null : submissionBasicInfoRepository.findBySubmissionId(submissionId).orElse(null);

        return ApprovalTaskVO.builder()
            .taskId(task.getId())
            .submissionId(submissionId)
            .documentNo(resolveDocumentNo(form, submissionId))
            .reportYear(form == null ? null : form.getReportYear())
            .submissionStatus(form == null ? null : form.getStatus().name())
            .enterpriseName(basic == null ? null : basic.getEnterpriseName())
            .nodeName(task.getNodeName())
            .roleCode(task.getRoleCode())
            .taskStatus(task.getStatus().name())
            .action(task.getAction() == null ? null : task.getAction().name())
            .comment(task.getCommentText())
            .submittedAt(form == null ? null : form.getSubmittedAt())
            .createdAt(task.getCreatedAt())
            .handledAt(task.getHandledAt())
            .build();
    }

    private boolean matchesTodoFilters(
        ApprovalTaskVO task,
        String normalizedDocumentNo,
        String normalizedEnterpriseName,
        LocalDateTime startTime,
        LocalDateTime endTime
    ) {
        if (StringUtils.hasText(normalizedDocumentNo)) {
            String doc = task.getDocumentNo() == null ? "" : task.getDocumentNo().trim().toLowerCase(Locale.ROOT);
            if (!doc.contains(normalizedDocumentNo)) {
                return false;
            }
        }
        if (StringUtils.hasText(normalizedEnterpriseName)) {
            String name = task.getEnterpriseName() == null ? "" : task.getEnterpriseName().trim().toLowerCase(Locale.ROOT);
            if (!name.contains(normalizedEnterpriseName)) {
                return false;
            }
        }
        LocalDateTime submittedAt = task.getSubmittedAt();
        if (startTime != null && (submittedAt == null || submittedAt.isBefore(startTime))) {
            return false;
        }
        if (endTime != null && (submittedAt == null || submittedAt.isAfter(endTime))) {
            return false;
        }
        return true;
    }

    private String normalizeKeyword(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : null;
    }

    private String resolveDocumentNo(SubmissionForm form, Long submissionId) {
        if (form != null && StringUtils.hasText(form.getDocumentNo())) {
            return form.getDocumentNo();
        }
        SubmissionBasicInfo basic = submissionId == null ? null : submissionBasicInfoRepository.findBySubmissionId(submissionId).orElse(null);
        String enterpriseName = basic == null ? null : basic.getEnterpriseName();
        return SubmissionNoUtils.buildNo(enterpriseName, submissionId, 0);
    }
}
