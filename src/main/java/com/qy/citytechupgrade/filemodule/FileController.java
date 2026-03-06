package com.qy.citytechupgrade.filemodule;

import com.qy.citytechupgrade.common.dto.ApiResponse;
import com.qy.citytechupgrade.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {
    private final FileService fileService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileService.FileUploadResult> upload(@RequestParam Long submissionId,
                                                            @RequestParam String attachmentType,
                                                            @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(fileService.upload(submissionId, attachmentType, file, SecurityUtils.currentUser()));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id) throws IOException {
        return buildBinaryResponse(fileService.getFile(id, SecurityUtils.currentUser()), "attachment");
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<InputStreamResource> preview(@PathVariable Long id) throws IOException {
        return buildBinaryResponse(fileService.getPreviewFile(id, SecurityUtils.currentUser()), "inline");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        fileService.deleteFile(id, SecurityUtils.currentUser());
        return ApiResponse.success("删除成功", null);
    }

    private ResponseEntity<InputStreamResource> buildBinaryResponse(FileService.DownloadFile file, String disposition) throws IOException {
        String contentType = file.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : file.getContentType();
        InputStreamResource resource = new InputStreamResource(Files.newInputStream(file.getPath()));

        ContentDisposition contentDisposition = "inline".equalsIgnoreCase(disposition)
            ? ContentDisposition.inline().filename(file.getFileName(), StandardCharsets.UTF_8).build()
            : ContentDisposition.attachment().filename(file.getFileName(), StandardCharsets.UTF_8).build();

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
            .contentType(MediaType.parseMediaType(contentType))
            .contentLength(Files.size(file.getPath()))
            .body(resource);
    }
}
