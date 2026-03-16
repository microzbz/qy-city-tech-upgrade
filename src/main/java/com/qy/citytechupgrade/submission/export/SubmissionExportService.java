package com.qy.citytechupgrade.submission.export;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.qy.citytechupgrade.audit.AuditService;
import com.qy.citytechupgrade.common.dto.PagedResult;
import com.qy.citytechupgrade.common.enums.SubmissionStatus;
import com.qy.citytechupgrade.common.exception.BizException;
import com.qy.citytechupgrade.common.security.CurrentUser;
import com.qy.citytechupgrade.config.AppProperties;
import com.qy.citytechupgrade.enterprise.EnterpriseService;
import com.qy.citytechupgrade.enterprise.EnterpriseService.SurveyEnterpriseCodeInfo;
import com.qy.citytechupgrade.industry.IndustryService;
import com.qy.citytechupgrade.industry.IndustryProcessOptionsResponse;
import com.qy.citytechupgrade.submission.SubmissionAttachment;
import com.qy.citytechupgrade.submission.SubmissionAttachmentRepository;
import com.qy.citytechupgrade.submission.SubmissionAttachmentVO;
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
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import java.util.LinkedHashMap;
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
    private static final String OTHER_OPTION = "其他";
    private static final Set<SubmissionStatus> APPROVED_RECORD_STATUSES = Set.of(
        SubmissionStatus.APPROVED,
        SubmissionStatus.RETURNED,
        SubmissionStatus.REJECTED
    );
    private static final Set<String> DEVICE_ATTACHMENT_TYPES = Set.of("DEVICE_PROOF", "PROOF");
    private static final Set<String> DIGITAL_ATTACHMENT_TYPES = Set.of("DIGITAL_PROOF");
    private static final Set<String> RD_TOOL_ATTACHMENT_TYPES = Set.of("RD_TOOL_PROOF");

    private final SubmissionFormRepository submissionFormRepository;
    private final SubmissionAttachmentRepository submissionAttachmentRepository;
    private final SubmissionBasicInfoRepository submissionBasicInfoRepository;
    private final SubmissionService submissionService;
    private final EnterpriseService enterpriseService;
    private final IndustryService industryService;
    private final AuditService auditService;
    private final AppProperties appProperties;

    private final Map<String, ExportJobState> jobs = new ConcurrentHashMap<>();
    private final ExecutorService exportExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "submission-export-worker");
        thread.setDaemon(true);
        return thread;
    });

    public PagedResult<ApprovedSubmissionListItemVO> listApprovedSubmissions(
        String companyName,
        String status,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer page,
        Integer size,
        CurrentUser currentUser
    ) {
        assertApprover(currentUser);
        List<ApprovedSubmissionListItemVO> items = findApprovedForms(companyName, status, startTime, endTime).stream()
            .map(this::toApprovedListItem)
            .toList();
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        int fromIndex = Math.min((safePage - 1) * safeSize, items.size());
        int toIndex = Math.min(fromIndex + safeSize, items.size());
        return PagedResult.of(items.subList(fromIndex, toIndex), items.size(), safePage, safeSize);
    }

    public ReportDownloadFile downloadReport(
        String companyName,
        String status,
        LocalDateTime startTime,
        LocalDateTime endTime,
        CurrentUser currentUser
    ) throws IOException {
        assertApprover(currentUser);
        List<SubmissionForm> forms = findApprovedForms(companyName, status, startTime, endTime);
        String fileName = buildReportFileName();
        Path reportDir = getExportRoot().resolve("reports");
        Path reportPath = reportDir.resolve(fileName);
        Files.createDirectories(reportDir);
        try (Workbook workbook = new XSSFWorkbook();
             OutputStream outputStream = Files.newOutputStream(reportPath)) {
            writeReportWorkbook(workbook, forms);
            workbook.write(outputStream);
        }
        auditService.log(currentUser.getUserId(), "EXPORT", "APPROVED_SUBMISSION_REPORT_EXPORT", fileName,
            "导出审批记录报表 " + forms.size() + " 条");
        return ReportDownloadFile.builder()
            .path(reportPath)
            .fileName(fileName)
            .build();
    }

    public ApprovedSubmissionExportJobVO createJob(ApprovedSubmissionExportRequest request, CurrentUser currentUser) {
        assertApprover(currentUser);
        List<Long> submissionIds = request.getSubmissionIds().stream()
            .filter(id -> id != null && id > 0)
            .distinct()
            .toList();
        if (submissionIds.isEmpty()) {
            throw new BizException("请选择至少一条可导出记录");
        }

        List<SubmissionForm> forms = submissionFormRepository.findAllById(submissionIds).stream()
            .filter(this::isZipExportAllowed)
            .sorted(Comparator.comparing(SubmissionForm::getUpdatedAt).reversed())
            .toList();
        if (forms.size() != submissionIds.size()) {
            throw new BizException("选中的记录中包含不可导出单据，请刷新后重试");
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
                "批量导出记录 " + job.getSuccessCount() + " 条");
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

        String exportPrefix = buildExportPrefix(codeInfo.exportCode());
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
        String html = normalizeHtmlForPdf(buildPdfHtml(detail, exportCode));
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

    private String normalizeHtmlForPdf(String html) {
        if (html == null) {
            return "";
        }
        return html
            .replace("&nbsp;", "&#160;");
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

    private List<SubmissionForm> findApprovedForms(
        String companyName,
        String status,
        LocalDateTime startTime,
        LocalDateTime endTime
    ) {
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            throw new BizException("开始时间不能晚于结束时间");
        }
        String normalizedCompanyName = normalizeKeyword(companyName);
        List<SubmissionStatus> statuses = resolveApprovedStatuses(status);
        return submissionFormRepository.findByStatusInOrderByUpdatedAtDesc(statuses).stream()
            .filter(form -> matchesCompanyName(form, normalizedCompanyName))
            .filter(form -> matchesHandledTime(form, startTime, endTime))
            .toList();
    }

    private List<SubmissionStatus> resolveApprovedStatuses(String status) {
        if (!StringUtils.hasText(status)) {
            return List.copyOf(APPROVED_RECORD_STATUSES);
        }
        SubmissionStatus resolved;
        try {
            resolved = SubmissionStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BizException("状态参数不正确");
        }
        if (!APPROVED_RECORD_STATUSES.contains(resolved)) {
            throw new BizException("仅支持查询已通过、已退回、已驳回记录");
        }
        return List.of(resolved);
    }

    private boolean matchesHandledTime(SubmissionForm form, LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime compareTime = form.getLastActionAt() != null ? form.getLastActionAt() : form.getUpdatedAt();
        if (compareTime == null) {
            return startTime == null && endTime == null;
        }
        if (startTime != null && compareTime.isBefore(startTime)) {
            return false;
        }
        if (endTime != null && compareTime.isAfter(endTime)) {
            return false;
        }
        return true;
    }

    private boolean matchesCompanyName(SubmissionForm form, String normalizedCompanyName) {
        if (!StringUtils.hasText(normalizedCompanyName)) {
            return true;
        }
        String enterpriseName = submissionBasicInfoRepository.findBySubmissionId(form.getId())
            .map(info -> info.getEnterpriseName())
            .orElse("");
        return enterpriseName.trim().toLowerCase(Locale.ROOT).contains(normalizedCompanyName);
    }

    private void writeReportWorkbook(Workbook workbook, List<SubmissionForm> forms) {
        Sheet sheet = workbook.createSheet("审批记录报表");
        CellStyle headerStyle = buildHeaderStyle(workbook);
        CellStyle wrapStyle = buildWrapStyle(workbook);
        List<String> headers = List.of(
            "单据号",
            "企业编码",
            "企业名称",
            "填报年份",
            "状态",
            "审批结果",
            "审批意见",
            "所属行业代码",
            "所属行业名称",
            "企业地址",
            "主要工序",
            "工序-设备对应",
            "信息化设备",
            "数字化系统",
            "研发工具",
            "技改设备附件名",
            "数字化系统附件名",
            "研发工具附件名"
        );
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(headerStyle);
        }

        int rowIndex = 1;
        for (SubmissionForm form : forms) {
            SubmissionDetailVO detail = submissionService.getDetailForExport(form.getId());
            Row row = sheet.createRow(rowIndex++);
            SubmissionSaveRequest.BasicInfo basic = detail.getBasicInfo();
            SubmissionSaveRequest.DeviceInfo device = detail.getDeviceInfo();
            SubmissionSaveRequest.DigitalInfo digital = detail.getDigitalInfo();
            SubmissionSaveRequest.RdToolInfo rdTool = detail.getRdToolInfo();
            List<SubmissionAttachmentVO> attachments = detail.getAttachments() == null ? List.of() : detail.getAttachments();
            SurveyEnterpriseCodeInfo codeInfo = basic == null || !StringUtils.hasText(basic.getEnterpriseName())
                ? null
                : enterpriseService.findSurveyEnterpriseCodeInfo(basic.getEnterpriseName());

            int col = 0;
            writeCell(row, col++, defaultText(detail.getDocumentNo()), wrapStyle);
            writeCell(row, col++, defaultText(codeInfo == null ? null : codeInfo.exportCode()), wrapStyle);
            writeCell(row, col++, defaultText(basic == null ? null : basic.getEnterpriseName()), wrapStyle);
            writeCell(row, col++, numberText(detail.getReportYear()), wrapStyle);
            writeCell(row, col++, toStatusLabel(form.getStatus()), wrapStyle);
            writeCell(row, col++, resolveReviewActionLabel(detail, form), wrapStyle);
            writeCell(row, col++, resolveReviewComment(detail, form), wrapStyle);
            writeCell(row, col++, defaultText(basic == null ? null : basic.getIndustryCode()), wrapStyle);
            writeCell(row, col++, defaultText(basic == null ? null : basic.getIndustryName()), wrapStyle);
            writeCell(row, col++, defaultText(basic == null ? null : basic.getAddress()), wrapStyle);
            writeCell(row, col++, buildProcessText(device), wrapStyle);
            writeCell(row, col++, buildProcessEquipmentText(detail), wrapStyle);
            writeCell(row, col++, buildInfoDeviceText(device), wrapStyle);
            writeCell(row, col++, buildDigitalSystemText(digital), wrapStyle);
            writeCell(row, col++, buildRdToolText(rdTool), wrapStyle);
            writeCell(row, col++, joinAttachmentNames(attachments, DEVICE_ATTACHMENT_TYPES), wrapStyle);
            writeCell(row, col++, joinAttachmentNames(attachments, DIGITAL_ATTACHMENT_TYPES), wrapStyle);
            writeCell(row, col++, joinAttachmentNames(attachments, RD_TOOL_ATTACHMENT_TYPES), wrapStyle);
            row.setHeightInPoints(54);
        }

        int[] widths = {18, 14, 28, 10, 10, 10, 24, 14, 18, 28, 18, 40, 22, 24, 24, 24, 24, 24};
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, widths[i] * 256);
        }
        sheet.createFreezePane(0, 1);
    }

    private CellStyle buildHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        XSSFFont font = (XSSFFont) workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle buildWrapStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setWrapText(true);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private void writeCell(Row row, int columnIndex, String value, CellStyle style) {
        Cell cell = row.createCell(columnIndex);
        cell.setCellValue(safeExcelText(value));
        cell.setCellStyle(style);
    }

    private String safeExcelText(String value) {
        String text = defaultText(value);
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) {
            return "'" + text;
        }
        return text;
    }

    private String resolveReviewActionLabel(SubmissionDetailVO detail, SubmissionForm form) {
        if (StringUtils.hasText(detail.getReviewActionLabel())) {
            return detail.getReviewActionLabel().trim();
        }
        return switch (form.getStatus()) {
            case APPROVED -> "通过";
            case RETURNED -> "退回修改";
            case REJECTED -> "驳回";
            default -> "-";
        };
    }

    private String resolveReviewComment(SubmissionDetailVO detail, SubmissionForm form) {
        if (StringUtils.hasText(detail.getReviewComment())) {
            return detail.getReviewComment().trim();
        }
        return form.getStatus() == SubmissionStatus.APPROVED ? "通过" : "-";
    }

    private String buildProcessText(SubmissionSaveRequest.DeviceInfo device) {
        if (device == null) {
            return "-";
        }
        List<String> parts = new ArrayList<>(normalizeOptions(device.getSelectedProcesses()));
        if (StringUtils.hasText(device.getOtherProcess())) {
            parts.add("其他：" + device.getOtherProcess().trim());
        }
        return joinLines(parts);
    }

    private String buildInfoDeviceText(SubmissionSaveRequest.DeviceInfo device) {
        if (device == null) {
            return "-";
        }
        List<String> parts = new ArrayList<>(normalizeOptions(device.getInfoDevices()));
        if (StringUtils.hasText(device.getOtherInfoDevice())) {
            parts.add("其他：" + device.getOtherInfoDevice().trim());
        }
        return joinLines(parts);
    }

    private String buildDigitalSystemText(SubmissionSaveRequest.DigitalInfo digital) {
        if (digital == null) {
            return "-";
        }
        List<String> parts = new ArrayList<>(normalizeOptions(digital.getDigitalSystems()));
        if (StringUtils.hasText(digital.getOtherSystem())) {
            parts.add("其他：" + digital.getOtherSystem().trim());
        }
        return joinLines(parts);
    }

    private String buildRdToolText(SubmissionSaveRequest.RdToolInfo rdTool) {
        if (rdTool == null) {
            return "-";
        }
        List<String> parts = new ArrayList<>(normalizeOptions(rdTool.getRdTools()));
        if (StringUtils.hasText(rdTool.getOtherTool())) {
            parts.add("其他：" + rdTool.getOtherTool().trim());
        }
        return joinLines(parts);
    }

    private String buildProcessEquipmentText(SubmissionDetailVO detail) {
        SubmissionSaveRequest.DeviceInfo device = detail.getDeviceInfo();
        SubmissionSaveRequest.BasicInfo basic = detail.getBasicInfo();
        if (device == null) {
            return "-";
        }
        List<String> selectedProcesses = normalizeOptions(device.getSelectedProcesses());
        List<String> selectedEquipments = normalizeOptions(device.getSelectedEquipments());
        if (selectedProcesses.isEmpty() && selectedEquipments.isEmpty() && !StringUtils.hasText(device.getOtherEquipment())) {
            return "-";
        }
        String industryCode = basic == null ? null : basic.getIndustryCode();
        if (!StringUtils.hasText(industryCode)) {
            return fallbackProcessEquipmentText(selectedProcesses, selectedEquipments, device);
        }

        IndustryProcessOptionsResponse processOptions;
        try {
            processOptions = industryService.listProcessOptions(industryCode.trim());
        } catch (Exception ex) {
            return fallbackProcessEquipmentText(selectedProcesses, selectedEquipments, device);
        }
        if (processOptions.isSpecialMode()) {
            List<String> lines = new ArrayList<>();
            if (!selectedProcesses.isEmpty()) {
                lines.add("工序：" + String.join("、", selectedProcesses));
            }
            List<String> equipmentParts = new ArrayList<>(selectedEquipments);
            if (StringUtils.hasText(device.getOtherEquipment())) {
                equipmentParts.add("其他：" + device.getOtherEquipment().trim());
            }
            if (!equipmentParts.isEmpty()) {
                lines.add("设备：" + String.join("、", equipmentParts));
            }
            return joinLines(lines);
        }

        ProcessDisplayMeta processMeta = buildProcessDisplayMeta(processOptions.getProcesses());
        Set<String> remainingEquipments = new LinkedHashSet<>(selectedEquipments);
        List<String> lines = new ArrayList<>();
        for (String processName : selectedProcesses) {
            List<String> queryNames = processMeta.queryMap().getOrDefault(processName, List.of(processName));
            Set<String> processEquipments = new LinkedHashSet<>();
            for (String queryName : queryNames) {
                processEquipments.addAll(industryService.listEquipments(industryCode.trim(), queryName));
            }
            List<String> matched = remainingEquipments.stream()
                .filter(processEquipments::contains)
                .toList();
            matched.forEach(remainingEquipments::remove);
            lines.add(processName + "：" + (matched.isEmpty() ? "-" : String.join("、", matched)));
        }
        if (StringUtils.hasText(device.getOtherProcess())) {
            lines.add("其他工序：" + device.getOtherProcess().trim());
        }
        if (StringUtils.hasText(device.getOtherEquipment())) {
            lines.add("其他设备：" + device.getOtherEquipment().trim());
        }
        if (!remainingEquipments.isEmpty()) {
            lines.add("未匹配工序设备：" + String.join("、", remainingEquipments));
        }
        return joinLines(lines);
    }

    private String fallbackProcessEquipmentText(
        List<String> selectedProcesses,
        List<String> selectedEquipments,
        SubmissionSaveRequest.DeviceInfo device
    ) {
        List<String> lines = new ArrayList<>();
        if (!selectedProcesses.isEmpty()) {
            lines.add("工序：" + String.join("、", selectedProcesses));
        }
        if (!selectedEquipments.isEmpty()) {
            lines.add("设备：" + String.join("、", selectedEquipments));
        }
        if (StringUtils.hasText(device.getOtherProcess())) {
            lines.add("其他工序：" + device.getOtherProcess().trim());
        }
        if (StringUtils.hasText(device.getOtherEquipment())) {
            lines.add("其他设备：" + device.getOtherEquipment().trim());
        }
        return joinLines(lines);
    }

    private String joinAttachmentNames(List<SubmissionAttachmentVO> attachments, Set<String> attachmentTypes) {
        if (attachments == null || attachments.isEmpty()) {
            return "-";
        }
        List<String> names = attachments.stream()
            .filter(attachment -> attachmentTypes.contains(attachment.getAttachmentType()))
            .map(SubmissionAttachmentVO::getOriginalFileName)
            .filter(StringUtils::hasText)
            .distinct()
            .toList();
        return names.isEmpty() ? "-" : String.join("\n", names);
    }

    private List<String> normalizeOptions(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            String text = value.trim();
            if (OTHER_OPTION.equals(text)) {
                continue;
            }
            normalized.add(text);
        }
        return new ArrayList<>(normalized);
    }

    private String joinLines(List<String> lines) {
        List<String> normalized = lines.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .filter(StringUtils::hasText)
            .toList();
        return normalized.isEmpty() ? "-" : String.join("\n", normalized);
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "-" : value.format(DISPLAY_TIME_FORMAT);
    }

    private String numberText(Number value) {
        return value == null ? "-" : String.valueOf(value);
    }

    private String defaultText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "-";
    }

    private String normalizeKeyword(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : null;
    }

    private String buildReportFileName() {
        return "审批记录报表_" + LocalDateTime.now().format(FILE_TIME_FORMAT) + ".xlsx";
    }

    private ProcessDisplayMeta buildProcessDisplayMeta(List<String> rawProcesses) {
        List<String> normalizedProcesses = normalizeOptions(rawProcesses);
        Set<String> exactSet = new LinkedHashSet<>(normalizedProcesses);
        Map<String, List<String>> specificMap = new LinkedHashMap<>();
        for (String processName : normalizedProcesses) {
            String baseName = extractBaseProcessName(processName);
            if (!StringUtils.hasText(baseName) || baseName.equals(processName) || !exactSet.contains(baseName)) {
                continue;
            }
            specificMap.computeIfAbsent(baseName, key -> new ArrayList<>()).add(processName);
        }

        Set<String> hiddenBaseSet = new LinkedHashSet<>(specificMap.keySet());
        Map<String, List<String>> queryMap = new LinkedHashMap<>();
        Set<String> emitted = new LinkedHashSet<>();
        for (String processName : normalizedProcesses) {
            if (hiddenBaseSet.contains(processName)) {
                for (String specificName : specificMap.getOrDefault(processName, List.of())) {
                    if (emitted.add(specificName)) {
                        queryMap.put(specificName, List.of(processName, specificName));
                    }
                }
                continue;
            }
            String baseName = extractBaseProcessName(processName);
            if (StringUtils.hasText(baseName) && !baseName.equals(processName) && hiddenBaseSet.contains(baseName)) {
                continue;
            }
            if (emitted.add(processName)) {
                queryMap.put(processName, List.of(processName));
            }
        }
        return new ProcessDisplayMeta(queryMap);
    }

    private String extractBaseProcessName(String processName) {
        if (!StringUtils.hasText(processName)) {
            return "";
        }
        String normalized = processName.trim();
        int englishIdx = normalized.indexOf('(');
        int chineseIdx = normalized.indexOf('（');
        int cutIndex = -1;
        if (englishIdx >= 0 && chineseIdx >= 0) {
            cutIndex = Math.min(englishIdx, chineseIdx);
        } else if (englishIdx >= 0) {
            cutIndex = englishIdx;
        } else if (chineseIdx >= 0) {
            cutIndex = chineseIdx;
        }
        return cutIndex < 0 ? normalized : normalized.substring(0, cutIndex).trim();
    }

    private ApprovedSubmissionListItemVO toApprovedListItem(SubmissionForm form) {
        SubmissionDetailVO detail = submissionService.getDetailForExport(form.getId());
        String enterpriseName = submissionBasicInfoRepository.findBySubmissionId(form.getId())
            .map(info -> info.getEnterpriseName())
            .orElse(null);
        String documentNo = form.getDocumentNo();
        if (!StringUtils.hasText(documentNo)) {
            documentNo = detail.getDocumentNo();
        }
        SurveyEnterpriseCodeInfo codeInfo = enterpriseService.findSurveyEnterpriseCodeInfo(enterpriseName);
        boolean exportable = isListExportable(form.getStatus(), codeInfo);
        return ApprovedSubmissionListItemVO.builder()
            .submissionId(form.getId())
            .documentNo(documentNo)
            .reportYear(form.getReportYear())
            .enterpriseName(enterpriseName)
            .status(form.getStatus().name())
            .statusLabel(toStatusLabel(form.getStatus()))
            .reviewActionLabel(detail.getReviewActionLabel())
            .reviewComment(detail.getReviewComment())
            .exportable(exportable)
            .exportHint(resolveExportHint(form.getStatus(), exportable))
            .submittedAt(form.getSubmittedAt())
            .reviewHandledAt(detail.getReviewHandledAt())
            .build();
    }

    private String resolveExportHint(SubmissionStatus status, boolean exportable) {
        if (status == SubmissionStatus.RETURNED) {
            return "已退回，不可导出";
        }
        return exportable ? "可导出" : "未匹配到调研企业编码";
    }

    private boolean isListExportable(SubmissionStatus status, SurveyEnterpriseCodeInfo codeInfo) {
        return isZipExportAllowed(status)
            && codeInfo != null
            && StringUtils.hasText(codeInfo.exportCode());
    }

    private boolean isZipExportAllowed(SubmissionForm form) {
        return isZipExportAllowed(form.getStatus());
    }

    private boolean isZipExportAllowed(SubmissionStatus status) {
        return status == SubmissionStatus.APPROVED || status == SubmissionStatus.REJECTED;
    }

    private String toStatusLabel(SubmissionStatus status) {
        if (status == null) {
            return "-";
        }
        return switch (status) {
            case APPROVED -> "已通过";
            case RETURNED -> "已退回";
            case REJECTED -> "已驳回";
            case DRAFT -> "草稿";
            case SUBMITTED, UNDER_REVIEW -> "审批中";
        };
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

    private String buildExportPrefix(String exportCode) {
        return exportCode;
    }

    private String buildZipFileName(ExportJobState job) {
        String timestamp = LocalDateTime.now().format(FILE_TIME_FORMAT);
        if (job.getTotalCount() == 1) {
            String folderName = job.getItems().stream()
                .filter(ApprovedSubmissionExportItemResultVO::isSuccess)
                .map(ApprovedSubmissionExportItemResultVO::getExportFolderName)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("导出结果");
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
    @Builder
    public static class ReportDownloadFile {
        private Path path;
        private String fileName;
    }

    private record ProcessDisplayMeta(Map<String, List<String>> queryMap) {
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
