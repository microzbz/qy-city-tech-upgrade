package com.qy.citytechupgrade.submission.export;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.qy.citytechupgrade.audit.AuditService;
import com.qy.citytechupgrade.common.enums.SubmissionStatus;
import com.qy.citytechupgrade.common.exception.BizException;
import com.qy.citytechupgrade.common.security.CurrentUser;
import com.qy.citytechupgrade.config.AppProperties;
import com.qy.citytechupgrade.enterprise.EnterpriseService;
import com.qy.citytechupgrade.enterprise.EnterpriseService.SurveyEnterpriseCodeInfo;
import com.qy.citytechupgrade.submission.SubmissionAttachment;
import com.qy.citytechupgrade.submission.SubmissionAttachmentRepository;
import com.qy.citytechupgrade.submission.SubmissionBasicInfoRepository;
import com.qy.citytechupgrade.submission.SubmissionDetailVO;
import com.qy.citytechupgrade.submission.SubmissionForm;
import com.qy.citytechupgrade.submission.SubmissionFormRepository;
import com.qy.citytechupgrade.submission.SubmissionSaveRequest;
import com.qy.citytechupgrade.submission.SubmissionService;
import jakarta.annotation.PreDestroy;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
public class SubmissionExportService {
    private static final Path CHINESE_FONT_PATH = Paths.get("/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf");
    private static final DateTimeFormatter FILE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter DISPLAY_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SubmissionFormRepository submissionFormRepository;
    private final SubmissionAttachmentRepository submissionAttachmentRepository;
    private final SubmissionBasicInfoRepository submissionBasicInfoRepository;
    private final SubmissionService submissionService;
    private final EnterpriseService enterpriseService;
    private final AuditService auditService;
    private final AppProperties appProperties;

    private final Map<String, ExportJobState> jobs = new ConcurrentHashMap<>();
    private final ExecutorService exportExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "submission-export-worker");
        thread.setDaemon(true);
        return thread;
    });

    public List<ApprovedSubmissionListItemVO> listApprovedSubmissions(CurrentUser currentUser) {
        assertApprover(currentUser);
        return submissionFormRepository.findByStatusInOrderByUpdatedAtDesc(List.of(SubmissionStatus.APPROVED)).stream()
            .map(this::toApprovedListItem)
            .toList();
    }

    public ApprovedSubmissionExportJobVO createJob(ApprovedSubmissionExportRequest request, CurrentUser currentUser) {
        assertApprover(currentUser);
        List<Long> submissionIds = request.getSubmissionIds().stream()
            .filter(id -> id != null && id > 0)
            .distinct()
            .toList();
        if (submissionIds.isEmpty()) {
            throw new BizException("请选择至少一条已审批通过记录");
        }

        List<SubmissionForm> forms = submissionFormRepository.findAllById(submissionIds).stream()
            .filter(form -> form.getStatus() == SubmissionStatus.APPROVED)
            .sorted(Comparator.comparing(SubmissionForm::getUpdatedAt).reversed())
            .toList();
        if (forms.size() != submissionIds.size()) {
            throw new BizException("选中的记录中包含非审批通过单据，请刷新后重试");
        }

        ExportJobState job = ExportJobState.create(forms);
        jobs.put(job.getJobId(), job);
        exportExecutor.submit(() -> runJob(job, currentUser));
        return toJobVO(job);
    }

    public ApprovedSubmissionExportJobVO getJob(String jobId, CurrentUser currentUser) {
        assertApprover(currentUser);
        ExportJobState job = jobs.get(jobId);
        if (job == null) {
            throw new BizException("导出任务不存在");
        }
        return toJobVO(job);
    }

    public ExportDownloadFile download(String jobId, CurrentUser currentUser) {
        assertApprover(currentUser);
        ExportJobState job = jobs.get(jobId);
        if (job == null) {
            throw new BizException("导出任务不存在");
        }
        if (!job.isDownloadReady() || job.getZipPath() == null || !Files.exists(job.getZipPath())) {
            throw new BizException("导出文件尚未生成完成");
        }
        return ExportDownloadFile.builder()
            .path(job.getZipPath())
            .fileName(job.getFileName())
            .build();
    }

    @PreDestroy
    public void shutdown() {
        exportExecutor.shutdownNow();
    }

    private void runJob(ExportJobState job, CurrentUser currentUser) {
        job.setStatus("RUNNING");
        job.setStartedAt(LocalDateTime.now());
        job.setMessage("导出任务处理中");

        Path jobRoot = getExportRoot().resolve(job.getJobId());
        Path batchRoot = jobRoot.resolve("batch");

        try {
            Files.createDirectories(batchRoot);
            for (SubmissionForm form : job.getForms()) {
                job.setCurrentSubmissionId(form.getId());
                SubmissionDetailVO detail = submissionService.getDetailForExport(form.getId());
                String enterpriseName = detail.getBasicInfo() == null ? null : detail.getBasicInfo().getEnterpriseName();
                job.setCurrentEnterpriseName(enterpriseName);
                try {
                    String folderName = exportSingleSubmission(detail, batchRoot);
                    job.addItem(form.getId(), enterpriseName, folderName, true, "导出成功");
                } catch (Exception ex) {
                    job.addItem(form.getId(), enterpriseName, null, false, ex.getMessage());
                }
                job.incrementCompletedCount();
            }

            if (job.getSuccessCount() <= 0) {
                job.setStatus("FAILED");
                job.setMessage("没有可下载的导出结果，请检查失败原因");
                job.setFinishedAt(LocalDateTime.now());
                return;
            }

            String fileName = buildZipFileName(job);
            Path zipPath = jobRoot.resolve(fileName);
            zipDirectory(batchRoot, zipPath);
            job.setZipPath(zipPath);
            job.setFileName(fileName);
            job.setStatus("COMPLETED");
            job.setMessage(job.getFailedCount() > 0
                ? "导出完成，部分记录失败，请查看明细"
                : "导出完成");
            job.setFinishedAt(LocalDateTime.now());
            auditService.log(currentUser.getUserId(), "EXPORT", "APPROVED_SUBMISSION_EXPORT", job.getJobId(),
                "批量导出审批通过单据 " + job.getSuccessCount() + " 条");
        } catch (Exception ex) {
            job.setStatus("FAILED");
            job.setMessage("导出失败: " + ex.getMessage());
            job.setFinishedAt(LocalDateTime.now());
        } finally {
            job.setCurrentSubmissionId(null);
            job.setCurrentEnterpriseName(null);
        }
    }

    private String exportSingleSubmission(SubmissionDetailVO detail, Path batchRoot) throws IOException {
        SubmissionSaveRequest.BasicInfo basicInfo = detail.getBasicInfo();
        if (basicInfo == null || !StringUtils.hasText(basicInfo.getEnterpriseName())) {
            throw new BizException("企业名称缺失，无法导出");
        }

        SurveyEnterpriseCodeInfo codeInfo = enterpriseService.findSurveyEnterpriseCodeInfo(basicInfo.getEnterpriseName());
        if (codeInfo == null || !StringUtils.hasText(codeInfo.exportCode())) {
            throw new BizException("企业未匹配到调研名单编码: " + basicInfo.getEnterpriseName());
        }

        String exportPrefix = buildExportPrefix(codeInfo.exportCode(), basicInfo.getEnterpriseName());
        String folderName = sanitizeFileName(exportPrefix);
        Path submissionDir = batchRoot.resolve(folderName);
        Path attachmentDir = submissionDir.resolve("附件");
        Files.createDirectories(attachmentDir);

        Path pdfPath = submissionDir.resolve(sanitizeFileName(exportPrefix + "_填报表.pdf"));
        renderPdf(detail, codeInfo.exportCode(), pdfPath);
        copyAttachments(detail.getSubmissionId(), attachmentDir, exportPrefix);

        return folderName;
    }

    private void copyAttachments(Long submissionId, Path attachmentDir, String exportPrefix) throws IOException {
        List<SubmissionAttachment> attachments = submissionAttachmentRepository.findBySubmissionIdOrderByUploadedAtDesc(submissionId);
        Set<String> usedNames = new LinkedHashSet<>();
        for (SubmissionAttachment attachment : attachments) {
            Path source = Paths.get(attachment.getFilePath());
            if (!Files.exists(source)) {
                throw new BizException("附件文件不存在: " + attachment.getOriginalFileName());
            }
            String originalName = StringUtils.hasText(attachment.getOriginalFileName()) ? attachment.getOriginalFileName() : "附件";
            String fileName = uniqueFileName(usedNames, sanitizeFileName(exportPrefix + "_" + originalName));
            Files.copy(source, attachmentDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void renderPdf(SubmissionDetailVO detail, String exportCode, Path targetPdf) throws IOException {
        String html = buildPdfHtml(detail, exportCode);
        Files.createDirectories(targetPdf.getParent());
        try (OutputStream outputStream = Files.newOutputStream(targetPdf)) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            if (Files.exists(CHINESE_FONT_PATH)) {
                builder.useFont(CHINESE_FONT_PATH.toFile(), "Droid Sans Fallback");
            }
            builder.withHtmlContent(html, targetPdf.getParent().toUri().toString());
            builder.toStream(outputStream);
            builder.run();
        } catch (Exception ex) {
            throw new BizException("PDF 生成失败: " + ex.getMessage());
        }
    }

    private String buildPdfHtml(SubmissionDetailVO detail, String exportCode) {
        SubmissionSaveRequest.BasicInfo basic = detail.getBasicInfo();
        SubmissionSaveRequest.DeviceInfo device = detail.getDeviceInfo();
        SubmissionSaveRequest.DigitalInfo digital = detail.getDigitalInfo();
        SubmissionSaveRequest.RdToolInfo rdTool = detail.getRdToolInfo();

        String enterpriseName = basic == null ? "" : safe(basic.getEnterpriseName());
        StringBuilder html = new StringBuilder();
        html.append("<html><head><meta charset='UTF-8' />")
            .append("<style>")
            .append("body{font-family:'Droid Sans Fallback',sans-serif;color:#222;font-size:12px;line-height:1.6;padding:18px;}")
            .append("h1{font-size:22px;margin:0 0 8px;} h2{font-size:16px;margin:18px 0 8px;padding:6px 10px;background:#f2f6fc;border-left:4px solid #409eff;}")
            .append(".meta{margin-bottom:12px;color:#666;} table{width:100%;border-collapse:collapse;margin-bottom:10px;table-layout:fixed;}")
            .append("th,td{border:1px solid #dcdfe6;padding:8px;vertical-align:top;word-break:break-all;} th{background:#fafafa;width:18%;text-align:left;}")
            .append(".list{white-space:pre-wrap;} .muted{color:#666;} ")
            .append("</style></head><body>");

        html.append("<h1>新型技改城市平台企业填报导出</h1>");
        html.append("<div class='meta'>")
            .append("企业编号: ").append(safe(exportCode))
            .append("　　企业名称: ").append(enterpriseName)
            .append("　　单据号: ").append(safe(detail.getDocumentNo()))
            .append("</div>");

        html.append("<h2>基本信息</h2><table>");
        appendRow(html, "企业名称", safe(basic == null ? null : basic.getEnterpriseName()), "统一社会信用代码", safe(basic == null ? null : basic.getCreditCode()));
        appendRow(html, "单位性质", safe(basic == null ? null : basic.getUnitNature()), "地址", safe(basic == null ? null : basic.getAddress()));
        appendRow(html, "成立时间", safeDate(basic == null ? null : basic.getEstablishedAt()), "注册资本(万元)", safeDecimal(basic == null ? null : basic.getRegisterCapital()));
        appendRow(html, "法人代表", safe(basic == null ? null : basic.getLegalPerson()), "主营产品", safe(basic == null ? null : basic.getMainProduct()));
        appendRow(html, "职工人数", safeNumber(basic == null ? null : basic.getEmployeeCount()), "所属行业代码", safe(basic == null ? null : basic.getIndustryCode()));
        appendRow(html, "所属行业名称", safe(basic == null ? null : basic.getIndustryName()), "填报年份", safeNumber(detail.getReportYear()));
        appendRow(html, "2023 年营收(万元)", safeDecimal(basic == null ? null : basic.getAnnualRevenue2023()),
            "2024 年营收(万元)", safeDecimal(basic == null ? null : basic.getAnnualRevenue2024()));
        appendRow(html, "2025 年营收(万元)", safeDecimal(basic == null ? null : basic.getAnnualRevenue2025()),
            "审批状态", safe(detail.getStatus()));
        html.append("</table>");

        html.append("<h2>关键工序及设备</h2><table>");
        appendSingleRow(html, "选择的关键工序", safeList(device == null ? null : device.getSelectedProcesses()));
        appendSingleRow(html, "选择的主要数控设备", safeList(device == null ? null : device.getSelectedEquipments()));
        appendSingleRow(html, "信息化设备", safeList(device == null ? null : device.getInfoDevices()));
        appendSingleRow(html, "其他工序", safe(device == null ? null : device.getOtherProcess()));
        appendSingleRow(html, "其他设备", safe(device == null ? null : device.getOtherEquipment()));
        appendSingleRow(html, "其他信息化设备", safe(device == null ? null : device.getOtherInfoDevice()));
        html.append("</table>");

        html.append("<h2>数字化系统</h2><table>");
        appendSingleRow(html, "已选系统", safeList(digital == null ? null : digital.getDigitalSystems()));
        appendSingleRow(html, "其他系统", safe(digital == null ? null : digital.getOtherSystem()));
        html.append("</table>");

        html.append("<h2>研发工具系统</h2><table>");
        appendSingleRow(html, "已选工具", safeList(rdTool == null ? null : rdTool.getRdTools()));
        appendSingleRow(html, "其他工具", safe(rdTool == null ? null : rdTool.getOtherTool()));
        html.append("</table>");

        html.append("<h2>审批信息</h2><table>");
        appendRow(html, "提交时间", safeDateTime(detail.getSubmittedAt()), "最后更新时间", safeDateTime(detail.getUpdatedAt()));
        appendRow(html, "审批结果", safe(detail.getReviewActionLabel()), "审批时间", safeDateTime(detail.getReviewHandledAt()));
        appendSingleRow(html, "审批意见", safe(detail.getReviewComment()));
        html.append("</table>");

        List<SubmissionAttachment> attachments = submissionAttachmentRepository.findBySubmissionIdOrderByUploadedAtDesc(detail.getSubmissionId());
        html.append("<h2>附件清单</h2><table>");
        html.append("<tr><th>附件类型</th><th>文件名</th><th>上传时间</th></tr>");
        if (attachments.isEmpty()) {
            html.append("<tr><td colspan='3' class='muted'>无附件</td></tr>");
        } else {
            for (SubmissionAttachment attachment : attachments) {
                html.append("<tr>")
                    .append("<td>").append(safe(attachmentTypeLabel(attachment.getAttachmentType()))).append("</td>")
                    .append("<td>").append(safe(attachment.getOriginalFileName())).append("</td>")
                    .append("<td>").append(safeDateTime(attachment.getUploadedAt())).append("</td>")
                    .append("</tr>");
            }
        }
        html.append("</table>");
        html.append("</body></html>");
        return html.toString();
    }

    private void appendRow(StringBuilder html, String label1, String value1, String label2, String value2) {
        html.append("<tr>")
            .append("<th>").append(label1).append("</th><td>").append(value1).append("</td>")
            .append("<th>").append(label2).append("</th><td>").append(value2).append("</td>")
            .append("</tr>");
    }

    private void appendSingleRow(StringBuilder html, String label, String value) {
        html.append("<tr>")
            .append("<th>").append(label).append("</th>")
            .append("<td colspan='3' class='list'>").append(value).append("</td>")
            .append("</tr>");
    }

    private void zipDirectory(Path sourceDir, Path targetZip) throws IOException {
        Files.createDirectories(targetZip.getParent());
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(Files.newOutputStream(targetZip));
             var walk = Files.walk(sourceDir)) {
            zos.setEncoding("UTF-8");
            zos.setUseLanguageEncodingFlag(true);
            zos.setCreateUnicodeExtraFields(ZipArchiveOutputStream.UnicodeExtraFieldPolicy.ALWAYS);
            zos.setFallbackToUTF8(true);
            walk.filter(Files::isRegularFile).forEach(path -> {
                try {
                    String entryName = sourceDir.relativize(path).toString().replace("\\", "/");
                    ZipArchiveEntry entry = new ZipArchiveEntry(path.toFile(), entryName);
                    zos.putArchiveEntry(entry);
                    Files.copy(path, zos);
                    zos.closeArchiveEntry();
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            });
            zos.finish();
        } catch (RuntimeException ex) {
            if (ex.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw ex;
        }
    }

    private ApprovedSubmissionListItemVO toApprovedListItem(SubmissionForm form) {
        String enterpriseName = submissionBasicInfoRepository.findBySubmissionId(form.getId())
            .map(info -> info.getEnterpriseName())
            .orElse(null);
        String documentNo = form.getDocumentNo();
        if (!StringUtils.hasText(documentNo)) {
            documentNo = submissionService.getDetailForExport(form.getId()).getDocumentNo();
        }
        SurveyEnterpriseCodeInfo codeInfo = enterpriseService.findSurveyEnterpriseCodeInfo(enterpriseName);
        boolean exportable = codeInfo != null && StringUtils.hasText(codeInfo.exportCode());
        return ApprovedSubmissionListItemVO.builder()
            .submissionId(form.getId())
            .documentNo(documentNo)
            .reportYear(form.getReportYear())
            .enterpriseName(enterpriseName)
            .exportable(exportable)
            .exportHint(exportable ? "可导出" : "未匹配到调研企业编码")
            .submittedAt(form.getSubmittedAt())
            .approvedAt(form.getLastActionAt())
            .build();
    }

    private ApprovedSubmissionExportJobVO toJobVO(ExportJobState job) {
        return ApprovedSubmissionExportJobVO.builder()
            .jobId(job.getJobId())
            .status(job.getStatus())
            .message(job.getMessage())
            .totalCount(job.getTotalCount())
            .completedCount(job.getCompletedCount())
            .successCount(job.getSuccessCount())
            .failedCount(job.getFailedCount())
            .currentSubmissionId(job.getCurrentSubmissionId())
            .currentEnterpriseName(job.getCurrentEnterpriseName())
            .fileName(job.getFileName())
            .downloadReady(job.isDownloadReady())
            .createdAt(job.getCreatedAt())
            .startedAt(job.getStartedAt())
            .finishedAt(job.getFinishedAt())
            .items(new ArrayList<>(job.getItems()))
            .build();
    }

    private void assertApprover(CurrentUser currentUser) {
        if (currentUser == null || (!currentUser.getRoles().contains("APPROVER_ADMIN") && !currentUser.getRoles().contains("SYS_ADMIN"))) {
            throw new BizException("无权限执行导出操作");
        }
    }

    private String buildExportPrefix(String exportCode, String enterpriseName) {
        return exportCode + "_" + enterpriseName;
    }

    private String buildZipFileName(ExportJobState job) {
        String timestamp = LocalDateTime.now().format(FILE_TIME_FORMAT);
        if (job.getTotalCount() == 1) {
            String folderName = job.getItems().stream()
                .filter(ApprovedSubmissionExportItemResultVO::isSuccess)
                .map(ApprovedSubmissionExportItemResultVO::getExportFolderName)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("已审批导出");
            return sanitizeFileName(folderName + "_" + timestamp) + ".zip";
        }
        return "已审批导出_批量导出_" + timestamp + ".zip";
    }

    private String uniqueFileName(Set<String> usedNames, String originalName) {
        String safeName = StringUtils.hasText(originalName) ? originalName : "附件";
        String candidate = safeName;
        String prefix = safeName;
        String suffix = "";
        int dotIndex = safeName.lastIndexOf('.');
        if (dotIndex > 0) {
            prefix = safeName.substring(0, dotIndex);
            suffix = safeName.substring(dotIndex);
        }
        int index = 2;
        while (!usedNames.add(candidate.toLowerCase(Locale.ROOT))) {
            candidate = prefix + "(" + index + ")" + suffix;
            index++;
        }
        return candidate;
    }

    private String sanitizeFileName(String input) {
        String source = StringUtils.hasText(input) ? input.trim() : UUID.randomUUID().toString();
        return source.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", "_");
    }

    private Path getExportRoot() {
        return Paths.get(appProperties.getFileStorage().getBasePath()).resolve(".exports");
    }

    private String safe(String value) {
        return HtmlUtils.htmlEscape(StringUtils.hasText(value) ? value.trim() : "-");
    }

    private String safeDate(LocalDate value) {
        return value == null ? "-" : HtmlUtils.htmlEscape(value.toString());
    }

    private String safeDateTime(LocalDateTime value) {
        return value == null ? "-" : HtmlUtils.htmlEscape(value.format(DISPLAY_TIME_FORMAT));
    }

    private String safeDecimal(BigDecimal value) {
        return value == null ? "-" : HtmlUtils.htmlEscape(value.stripTrailingZeros().toPlainString());
    }

    private String safeNumber(Number value) {
        return value == null ? "-" : HtmlUtils.htmlEscape(String.valueOf(value));
    }

    private String safeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "-";
        }
        return HtmlUtils.htmlEscape(String.join("\n", values));
    }

    private String attachmentTypeLabel(String attachmentType) {
        if ("DEVICE_PROOF".equals(attachmentType) || "PROOF".equals(attachmentType)) {
            return "技改设备投入材料";
        }
        if ("DIGITAL_PROOF".equals(attachmentType)) {
            return "数字化系统材料";
        }
        if ("RD_TOOL_PROOF".equals(attachmentType)) {
            return "研发工具材料";
        }
        return attachmentType;
    }

    @Data
    @Builder
    public static class ExportDownloadFile {
        private Path path;
        private String fileName;
    }

    @Data
    private static class ExportJobState {
        private final String jobId;
        private final List<SubmissionForm> forms;
        private final LocalDateTime createdAt;
        private final int totalCount;
        private final List<ApprovedSubmissionExportItemResultVO> items = new CopyOnWriteArrayList<>();
        private volatile String status;
        private volatile String message;
        private volatile int completedCount;
        private volatile int successCount;
        private volatile int failedCount;
        private volatile Long currentSubmissionId;
        private volatile String currentEnterpriseName;
        private volatile String fileName;
        private volatile Path zipPath;
        private volatile LocalDateTime startedAt;
        private volatile LocalDateTime finishedAt;

        static ExportJobState create(List<SubmissionForm> forms) {
            ExportJobState state = new ExportJobState(UUID.randomUUID().toString(), forms, LocalDateTime.now(), forms.size());
            state.setStatus("QUEUED");
            state.setMessage("导出任务已进入队列");
            return state;
        }

        ExportJobState(String jobId, List<SubmissionForm> forms, LocalDateTime createdAt, int totalCount) {
            this.jobId = jobId;
            this.forms = forms;
            this.createdAt = createdAt;
            this.totalCount = totalCount;
        }

        synchronized void addItem(Long submissionId, String enterpriseName, String folderName, boolean success, String message) {
            items.add(ApprovedSubmissionExportItemResultVO.builder()
                .submissionId(submissionId)
                .enterpriseName(enterpriseName)
                .exportFolderName(folderName)
                .success(success)
                .message(message)
                .build());
            if (success) {
                successCount++;
            } else {
                failedCount++;
            }
        }

        synchronized void incrementCompletedCount() {
            completedCount++;
        }

        synchronized boolean isDownloadReady() {
            return "COMPLETED".equals(status) && zipPath != null;
        }
    }
}
