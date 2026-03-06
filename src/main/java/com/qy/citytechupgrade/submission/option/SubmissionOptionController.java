package com.qy.citytechupgrade.submission.option;

import com.qy.citytechupgrade.common.dto.ApiResponse;
import com.qy.citytechupgrade.common.dto.PagedResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/submission/options")
@RequiredArgsConstructor
public class SubmissionOptionController {
    private final SubmissionOptionService submissionOptionService;

    @GetMapping("/digital-systems")
    public ApiResponse<List<SubmissionOptionItem>> digitalSystemOptions() {
        return ApiResponse.success(submissionOptionService.listDigitalSystemOptions());
    }

    @GetMapping("/rd-tools")
    public ApiResponse<List<SubmissionOptionItem>> rdToolOptions() {
        return ApiResponse.success(submissionOptionService.listRdToolOptions());
    }

    @GetMapping("/admin/digital-systems")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    public ApiResponse<PagedResult<DigitalSystemOption>> listDigitalSystemOptionsForAdmin(
        @RequestParam(required = false) String optionName,
        @RequestParam(required = false) Boolean enabled,
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "20") Integer size
    ) {
        return ApiResponse.success(submissionOptionService.listDigitalSystemOptionsForAdmin(optionName, enabled, page, size));
    }

    @PostMapping("/admin/digital-systems")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    public ApiResponse<DigitalSystemOption> createDigitalSystemOption(@RequestBody @Valid SubmissionOptionUpsertRequest request) {
        return ApiResponse.success(submissionOptionService.createDigitalSystemOption(request));
    }

    @PutMapping("/admin/digital-systems/{id}")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    public ApiResponse<DigitalSystemOption> updateDigitalSystemOption(@PathVariable Long id,
                                                                      @RequestBody @Valid SubmissionOptionUpsertRequest request) {
        return ApiResponse.success(submissionOptionService.updateDigitalSystemOption(id, request));
    }

    @DeleteMapping("/admin/digital-systems/{id}")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    public ApiResponse<String> deleteDigitalSystemOption(@PathVariable Long id) {
        submissionOptionService.deleteDigitalSystemOption(id);
        return ApiResponse.success("删除成功", "OK");
    }

    @GetMapping("/admin/rd-tools")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    public ApiResponse<PagedResult<RdToolOption>> listRdToolOptionsForAdmin(
        @RequestParam(required = false) String optionName,
        @RequestParam(required = false) Boolean enabled,
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "20") Integer size
    ) {
        return ApiResponse.success(submissionOptionService.listRdToolOptionsForAdmin(optionName, enabled, page, size));
    }

    @PostMapping("/admin/rd-tools")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    public ApiResponse<RdToolOption> createRdToolOption(@RequestBody @Valid SubmissionOptionUpsertRequest request) {
        return ApiResponse.success(submissionOptionService.createRdToolOption(request));
    }

    @PutMapping("/admin/rd-tools/{id}")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    public ApiResponse<RdToolOption> updateRdToolOption(@PathVariable Long id,
                                                        @RequestBody @Valid SubmissionOptionUpsertRequest request) {
        return ApiResponse.success(submissionOptionService.updateRdToolOption(id, request));
    }

    @DeleteMapping("/admin/rd-tools/{id}")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    public ApiResponse<String> deleteRdToolOption(@PathVariable Long id) {
        submissionOptionService.deleteRdToolOption(id);
        return ApiResponse.success("删除成功", "OK");
    }
}
