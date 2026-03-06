package com.qy.citytechupgrade.approval;

import com.qy.citytechupgrade.audit.AuditService;
import com.qy.citytechupgrade.common.enums.SubmissionStatus;
import com.qy.citytechupgrade.common.enums.TaskAction;
import com.qy.citytechupgrade.common.enums.TaskStatus;
import com.qy.citytechupgrade.common.enums.WorkflowStatus;
import com.qy.citytechupgrade.common.exception.BizException;
import com.qy.citytechupgrade.common.security.CurrentUser;
import com.qy.citytechupgrade.common.util.SubmissionNoUtils;
import com.qy.citytechupgrade.notification.NoticeService;
import com.qy.citytechupgrade.submission.SubmissionBasicInfo;
import com.qy.citytechupgrade.submission.SubmissionBasicInfoRepository;
import com.qy.citytechupgrade.submission.SubmissionForm;
import com.qy.citytechupgrade.submission.SubmissionFormRepository;
import com.qy.citytechupgrade.submission.SubmissionService;
import com.qy.citytechupgrade.user.SysUser;
import com.qy.citytechupgrade.user.UserService;
import com.qy.citytechupgrade.workflow.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ApprovalService {
    private final WfInstanceRepository wfInstanceRepository;
    private final WfTaskRepository wfTaskRepository;
    private final WorkflowService workflowService;
    private final SubmissionService submissionService;
    private final SubmissionFormRepository submissionFormRepository;
    private final SubmissionBasicInfoRepository submissionBasicInfoRepository;
    private final NoticeService noticeService;
    private final UserService userService;
    private final AuditService auditService;

    @Transactional
    public void startWorkflowForSubmission(Long submissionId, CurrentUser operator) {
        SubmissionForm form = submissionService.getByIdOrThrow(submissionId);
        if (form.getStatus() != SubmissionStatus.SUBMITTED) {
            throw new BizException("当前单据不在可提交流程状态");
        }
        String documentNo = resolveDocumentNo(form, submissionId);
        if (!workflowService.isApprovalEnabled(WorkflowService.BIZ_TYPE_SUBMISSION)) {
            submissionService.updateReviewNode(submissionId, SubmissionStatus.APPROVED, null, null);
            notifyEnterpriseUsers(submissionId, "审批结果通知", "单据号 " + documentNo + " 已自动通过");
            auditService.log(operator.getUserId(), "WORKFLOW", "AUTO_APPROVE", String.valueOf(submissionId), "审批开关关闭，自动通过");
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
                wfInstanceRepository.delete(instance);
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

        auditService.log(operator.getUserId(), "WORKFLOW", "START", String.valueOf(submissionId), "发起审批流程");
    }

    public List<ApprovalTaskVO> todo(CurrentUser currentUser) {
        List<WfTask> tasks;
        if (currentUser.getRoles().contains("SYS_ADMIN")) {
            tasks = wfTaskRepository.findByStatusOrderByUpdatedAtDesc(TaskStatus.TODO);
        } else {
            tasks = wfTaskRepository.findByStatusAndRoleCodeInOrderByCreatedAtDesc(TaskStatus.TODO, currentUser.getRoles().stream().toList());
        }
        return tasks.stream().map(this::toTaskVO).toList();
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

    private void handle(Long taskId, TaskAction action, String comment, CurrentUser operator) {
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

        auditService.log(operator.getUserId(), "APPROVAL", "APPROVE", String.valueOf(submissionId), "节点通过");
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
        auditService.log(operator.getUserId(), "APPROVAL", actionName, String.valueOf(submissionId), comment);
    }

    private void notifyApprovers(String roleCode, String title, String content) {
        List<SysUser> users = userService.listUsersByRoleCode(roleCode);
        for (SysUser user : users) {
            noticeService.push(user.getId(), title, content);
        }
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
            .createdAt(task.getCreatedAt())
            .handledAt(task.getHandledAt())
            .build();
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
