package com.qy.citytechupgrade.filemodule;

import com.qy.citytechupgrade.audit.AuditService;
import com.qy.citytechupgrade.common.exception.BizException;
import com.qy.citytechupgrade.common.enums.SubmissionStatus;
import com.qy.citytechupgrade.common.security.CurrentUser;
import com.qy.citytechupgrade.config.AppProperties;
import com.qy.citytechupgrade.submission.SubmissionAttachment;
import com.qy.citytechupgrade.submission.SubmissionAttachmentRepository;
import com.qy.citytechupgrade.submission.SubmissionForm;
import com.qy.citytechupgrade.submission.SubmissionFormRepository;
import com.qy.citytechupgrade.submission.SubmissionService;
import jakarta.transaction.Transactional;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Comparator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class FileService {
    private static final Set<String> DIRECT_PREVIEW_EXT = Set.of(
        "pdf", "png", "jpg", "jpeg", "gif", "bmp", "webp", "txt", "csv", "json", "xml", "html", "htm", "md"
    );

    private static final Set<String> OFFICE_TO_PDF_EXT = Set.of(
        "doc", "docx", "ppt", "pptx", "wps", "dps"
    );
    private static final Set<String> DEVICE_ATTACHMENT_TYPES = Set.of("DEVICE_PROOF", "PROOF");
    private static final Set<String> DEVICE_ALLOWED_EXT = Set.of("xls", "xlsx", "pdf");
    private static final Set<String> PDF_ONLY_ATTACHMENT_TYPES = Set.of("DIGITAL_PROOF", "RD_TOOL_PROOF");

    private final AppProperties appProperties;
    private final SubmissionService submissionService;
    private final SubmissionAttachmentRepository submissionAttachmentRepository;
    private final SubmissionFormRepository submissionFormRepository;
    private final AuditService auditService;

    public FileUploadResult upload(Long submissionId,
                                   String attachmentType,
                                   MultipartFile file,
                                   CurrentUser currentUser) {
        log.info("[文件] 开始上传附件，submissionId={}，attachmentType={}，userId={}，roles={}，fileName={}，size={}",
            submissionId, attachmentType, currentUser.getUserId(), currentUser.getRoles(),
            file == null ? null : file.getOriginalFilename(), file == null ? 0 : file.getSize());
        if (file == null || file.isEmpty()) {
            throw new BizException("上传文件不能为空");
        }
        if (attachmentType == null || attachmentType.isBlank()) {
            throw new BizException("attachmentType不能为空");
        }
        validateDeviceAttachmentType(file, attachmentType);
        validatePdfOnlyAttachmentType(file, attachmentType);

        SubmissionForm form = submissionFormRepository.findById(submissionId)
            .orElseThrow(() -> new BizException("填报单不存在"));
        if (currentUser.getRoles().contains("ENTERPRISE_USER")) {
            if (!form.getEnterpriseId().equals(currentUser.getEnterpriseId())) {
                throw new BizException("无权限上传该附件");
            }
            SubmissionForm latest = submissionFormRepository.findTopByEnterpriseIdOrderByUpdatedAtDesc(currentUser.getEnterpriseId())
                .orElseThrow(() -> new BizException("填报单不存在"));
            if (!latest.getId().equals(form.getId())) {
                throw new BizException("当前仅允许修改企业最新的一份填报");
            }
            if (!submissionService.isEditableStatus(form.getStatus())) {
                throw new BizException("当前状态不可上传附件");
            }
        } else if (currentUser.getRoles().contains("APPROVER_ADMIN") || currentUser.getRoles().contains("SYS_ADMIN")) {
            if (!submissionService.isApproverEditableStatus(form.getStatus())) {
                throw new BizException("当前状态不允许管理员上传附件");
            }
        } else {
            throw new BizException("当前用户无权上传附件");
        }

        String ext = getExt(file.getOriginalFilename());
        String relativeDir = buildSubDirByConfig();
        String relativePath = relativeDir + "/" + UUID.randomUUID().toString().replace("-", "") + ext;

        Path base = Paths.get(appProperties.getFileStorage().getBasePath());
        Path target = base.resolve(relativePath);
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException e) {
            throw new BizException("文件保存失败");
        }

        submissionService.attachFile(
            submissionId,
            attachmentType,
            target.toAbsolutePath().toString(),
            file.getOriginalFilename(),
            file.getContentType(),
            currentUser.getUserId()
        );

        auditService.log(currentUser.getUserId(), "FILE", "UPLOAD",
            String.valueOf(submissionId), attachmentType + ":" + file.getOriginalFilename());
        log.info("[文件] 附件上传成功，submissionId={}，attachmentType={}，userId={}，storedPath={}，fileName={}",
            submissionId, attachmentType, currentUser.getUserId(), target.toAbsolutePath(), file.getOriginalFilename());

        return FileUploadResult.builder().path(relativePath).fileName(file.getOriginalFilename()).build();
    }

    public DownloadFile getFile(Long attachmentId, CurrentUser currentUser) {
        AttachmentResolved resolved = resolveAttachment(attachmentId, currentUser);
        return DownloadFile.builder()
            .path(resolved.path)
            .contentType(resolveContentType(resolved.path, resolved.attachment.getContentType()))
            .fileName(resolved.attachment.getOriginalFileName())
            .build();
    }

    public DownloadFile getPreviewFile(Long attachmentId, CurrentUser currentUser) {
        AttachmentResolved resolved = resolveAttachment(attachmentId, currentUser);
        String ext = getExt(resolved.attachment.getOriginalFileName()).replace(".", "").toLowerCase(Locale.ROOT);

        if (DIRECT_PREVIEW_EXT.contains(ext)) {
            return DownloadFile.builder()
                .path(resolved.path)
                .contentType(resolveContentType(resolved.path, resolved.attachment.getContentType()))
                .fileName(resolved.attachment.getOriginalFileName())
                .build();
        }

        if (OFFICE_TO_PDF_EXT.contains(ext)) {
            Path pdfPath = convertOfficeToPdf(resolved.attachment.getId(), resolved.path);
            return DownloadFile.builder()
                .path(pdfPath)
                .contentType(MediaType.APPLICATION_PDF_VALUE)
                .fileName(removeExt(resolved.attachment.getOriginalFileName()) + ".pdf")
                .build();
        }

        throw new BizException("当前文件类型不支持在线预览，请下载查看");
    }

    @Transactional
    public void deleteFile(Long attachmentId, CurrentUser currentUser) {
        log.info("[文件] 开始删除附件，attachmentId={}，userId={}，roles={}",
            attachmentId, currentUser.getUserId(), currentUser.getRoles());
        SubmissionAttachment attachment = submissionAttachmentRepository.findById(attachmentId)
            .orElseThrow(() -> new BizException("附件不存在"));
        SubmissionForm form = submissionFormRepository.findById(attachment.getSubmissionId())
            .orElseThrow(() -> new BizException("填报单不存在"));

        if (currentUser.getRoles().contains("ENTERPRISE_USER")) {
            if (!form.getEnterpriseId().equals(currentUser.getEnterpriseId())) {
                throw new BizException("无权限删除该附件");
            }
            SubmissionForm latest = submissionFormRepository.findTopByEnterpriseIdOrderByUpdatedAtDesc(currentUser.getEnterpriseId())
                .orElseThrow(() -> new BizException("填报单不存在"));
            if (!latest.getId().equals(form.getId())) {
                throw new BizException("当前仅允许修改企业最新的一份填报");
            }
            if (!submissionService.isEditableStatus(form.getStatus())) {
                throw new BizException("当前状态不可删除附件");
            }
        } else if (currentUser.getRoles().contains("APPROVER_ADMIN") || currentUser.getRoles().contains("SYS_ADMIN")) {
            if (!submissionService.isApproverEditableStatus(form.getStatus())) {
                throw new BizException("当前状态不允许管理员删除附件");
            }
        } else {
            throw new BizException("当前用户无权删除附件");
        }

        Path filePath = Paths.get(attachment.getFilePath());
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new BizException("删除附件文件失败");
        }

        deletePreviewCache(attachmentId);
        submissionAttachmentRepository.delete(attachment);
        auditService.log(currentUser.getUserId(), "FILE", "DELETE",
            String.valueOf(form.getId()), attachment.getAttachmentType() + ":" + attachment.getOriginalFileName());
        log.info("[文件] 附件删除成功，attachmentId={}，submissionId={}，attachmentType={}，fileName={}，userId={}",
            attachmentId, form.getId(), attachment.getAttachmentType(), attachment.getOriginalFileName(), currentUser.getUserId());
    }

    private AttachmentResolved resolveAttachment(Long attachmentId, CurrentUser currentUser) {
        SubmissionAttachment attachment = submissionAttachmentRepository.findById(attachmentId)
            .orElseThrow(() -> new BizException("附件不存在"));

        SubmissionForm form = submissionFormRepository.findById(attachment.getSubmissionId())
            .orElseThrow(() -> new BizException("填报单不存在"));

        if (currentUser.getRoles().contains("ENTERPRISE_USER") && !form.getEnterpriseId().equals(currentUser.getEnterpriseId())) {
            throw new BizException("无权限访问该附件");
        }

        Path path = Paths.get(attachment.getFilePath());
        if (!Files.exists(path)) {
            throw new BizException("附件文件不存在");
        }

        return new AttachmentResolved(attachment, path);
    }

    private void validateDeviceAttachmentType(MultipartFile file, String attachmentType) {
        if (!DEVICE_ATTACHMENT_TYPES.contains(attachmentType)) {
            return;
        }
        String ext = getExt(file.getOriginalFilename()).replace(".", "").toLowerCase(Locale.ROOT);
        if (DEVICE_ALLOWED_EXT.contains(ext)) {
            return;
        }
        throw new BizException("技改设备投入材料仅支持Excel(.xls/.xlsx)和PDF(.pdf)格式");
    }

    private void validatePdfOnlyAttachmentType(MultipartFile file, String attachmentType) {
        if (!PDF_ONLY_ATTACHMENT_TYPES.contains(attachmentType)) {
            return;
        }
        String ext = getExt(file.getOriginalFilename()).replace(".", "").toLowerCase(Locale.ROOT);
        if ("pdf".equals(ext)) {
            return;
        }
        if ("DIGITAL_PROOF".equals(attachmentType)) {
            throw new BizException("数字化系统材料仅支持PDF(.pdf)格式");
        }
        throw new BizException("研发工具材料仅支持PDF(.pdf)格式");
    }

    private Path convertOfficeToPdf(Long attachmentId, Path sourcePath) {
        Path previewRoot = getPreviewRootPath();
        Path previewDir = previewRoot.resolve(String.valueOf(attachmentId));
        String outputName = removeExt(sourcePath.getFileName().toString()) + ".pdf";
        Path output = previewDir.resolve(outputName);

        try {
            Files.createDirectories(previewDir);
            if (Files.exists(output) && Files.getLastModifiedTime(output).toMillis() >= Files.getLastModifiedTime(sourcePath).toMillis()) {
                log.info("[文件] 预览转换命中缓存，attachmentId={}，sourcePath={}，outputPath={}",
                    attachmentId, sourcePath, output);
                return output;
            }

            log.info("[文件] 开始执行预览转换，attachmentId={}，sourcePath={}，outputPath={}",
                attachmentId, sourcePath, output);
            ProcessBuilder pb = new ProcessBuilder(
                "libreoffice",
                "--headless",
                "--convert-to",
                "pdf",
                "--outdir",
                previewDir.toAbsolutePath().toString(),
                sourcePath.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("[文件] 预览转换超时，attachmentId={}，sourcePath={}", attachmentId, sourcePath);
                throw new BizException("文档预览转换超时");
            }
            if (process.exitValue() != 0 || !Files.exists(output)) {
                log.warn("[文件] 预览转换失败，attachmentId={}，sourcePath={}，exitCode={}",
                    attachmentId, sourcePath, process.exitValue());
                throw new BizException("文档预览转换失败");
            }
            log.info("[文件] 预览转换成功，attachmentId={}，sourcePath={}，outputPath={}",
                attachmentId, sourcePath, output);
            return output;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[文件] 预览转换被中断，attachmentId={}，sourcePath={}", attachmentId, sourcePath, e);
            throw new BizException("文档预览转换失败: " + e.getMessage());
        } catch (IOException e) {
            log.warn("[文件] 预览转换发生 IO 异常，attachmentId={}，sourcePath={}", attachmentId, sourcePath, e);
            throw new BizException("文档预览转换失败: " + e.getMessage());
        }
    }

    private String resolveContentType(Path path, String fallback) {
        try {
            String probe = Files.probeContentType(path);
            if (probe != null && !probe.isBlank()) {
                return probe;
            }
        } catch (IOException ignored) {
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    private String getExt(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.'));
    }

    private String removeExt(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return fileName == null ? "file" : fileName;
        }
        return fileName.substring(0, fileName.lastIndexOf('.'));
    }

    private String buildSubDirByConfig() {
        String pattern = appProperties.getFileStorage().getSubDirPattern();
        String configured = pattern == null ? "" : pattern.trim();
        if (configured.isBlank()) {
            configured = "yyyy/M/d";
        }
        try {
            return LocalDate.now().format(DateTimeFormatter.ofPattern(configured));
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            throw new BizException("app.file-storage.sub-dir-pattern 配置无效");
        }
    }

    private Path getPreviewRootPath() {
        String preview = appProperties.getFileStorage().getPreviewBasePath();
        if (preview != null && !preview.trim().isBlank()) {
            return Paths.get(preview.trim());
        }
        return Paths.get(appProperties.getFileStorage().getBasePath()).resolve(".preview");
    }

    private void deletePreviewCache(Long attachmentId) {
        Path previewDir = getPreviewRootPath().resolve(String.valueOf(attachmentId));
        if (!Files.exists(previewDir)) {
            return;
        }
        try (var walk = Files.walk(previewDir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    @Data
    @Builder
    public static class FileUploadResult {
        private String path;
        private String fileName;
    }

    @Data
    @Builder
    public static class DownloadFile {
        private Path path;
        private String fileName;
        private String contentType;
    }

    private record AttachmentResolved(SubmissionAttachment attachment, Path path) {
    }
}
