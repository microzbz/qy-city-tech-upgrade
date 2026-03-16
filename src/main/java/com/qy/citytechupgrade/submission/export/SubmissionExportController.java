package com.qy.citytechupgrade.submission.export;

import com.qy.citytechupgrade.common.dto.ApiResponse;
import com.qy.citytechupgrade.common.dto.PagedResult;
import com.qy.citytechupgrade.common.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/submission-exports")
@RequiredArgsConstructor
public class SubmissionExportController {
    private final SubmissionExportService submissionExportService;

    @GetMapping("/approved-list")
    @PreAuthorize("hasAnyRole('APPROVER_ADMIN','SYS_ADMIN')")
    public ApiResponse<PagedResult<ApprovedSubmissionListItemVO>> approvedList(
        @RequestParam(required = false) String companyName,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "20") Integer size
    ) {
        return ApiResponse.success(submissionExportService.listApprovedSubmissions(
            companyName, status, startTime, endTime, page, size, SecurityUtils.currentUser()
        ));
    }

    @PostMapping("/jobs")
    @PreAuthorize("hasAnyRole('APPROVER_ADMIN','SYS_ADMIN')")
    public ApiResponse<ApprovedSubmissionExportJobVO> createJob(@RequestBody @Valid ApprovedSubmissionExportRequest request) {
        return ApiResponse.success(submissionExportService.createJob(request, SecurityUtils.currentUser()));
    }

    @GetMapping("/jobs/{jobId}")
    @PreAuthorize("hasAnyRole('APPROVER_ADMIN','SYS_ADMIN')")
    public ApiResponse<ApprovedSubmissionExportJobVO> jobDetail(@PathVariable String jobId) {
        return ApiResponse.success(submissionExportService.getJob(jobId, SecurityUtils.currentUser()));
    }

    @GetMapping("/jobs/{jobId}/download")
    @PreAuthorize("hasAnyRole('APPROVER_ADMIN','SYS_ADMIN')")
    public ResponseEntity<InputStreamResource> download(@PathVariable String jobId) throws IOException {
        SubmissionExportService.ExportDownloadFile file = submissionExportService.download(jobId, SecurityUtils.currentUser());
        return ResponseEntity.ok()
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(file.getFileName(), StandardCharsets.UTF_8).build().toString()
            )
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .contentLength(Files.size(file.getPath()))
            .body(new InputStreamResource(Files.newInputStream(file.getPath())));
    }

    @GetMapping("/report")
    @PreAuthorize("hasAnyRole('APPROVER_ADMIN','SYS_ADMIN')")
    public ResponseEntity<InputStreamResource> downloadReport(
        @RequestParam(required = false) String companyName,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime
    ) throws IOException {
        SubmissionExportService.ReportDownloadFile file =
            submissionExportService.downloadReport(companyName, status, startTime, endTime, SecurityUtils.currentUser());
        InputStream inputStream = Files.newInputStream(file.getPath());
        return ResponseEntity.ok()
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(file.getFileName(), StandardCharsets.UTF_8).build().toString()
            )
            .contentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            ))
            .contentLength(Files.size(file.getPath()))
            .body(new InputStreamResource(inputStream));
    }
}
