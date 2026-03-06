package com.qy.citytechupgrade.industry;

import com.qy.citytechupgrade.audit.AuditService;
import com.qy.citytechupgrade.common.dto.ApiResponse;
import com.qy.citytechupgrade.common.dto.PagedResult;
import com.qy.citytechupgrade.common.security.SecurityUtils;
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
@RequestMapping("/api/industry")
@RequiredArgsConstructor
public class IndustryController {
    private final IndustryService industryService;
    private final AuditService auditService;

    @GetMapping("/processes")
    public ApiResponse<List<String>> processes(@RequestParam String industryCode) {
        return ApiResponse.success(industryService.listProcesses(industryCode));
    }

    @GetMapping("/equipments")
    public ApiResponse<List<String>> equipments(@RequestParam String industryCode,
                                                @RequestParam String processName) {
        return ApiResponse.success(industryService.listEquipments(industryCode, processName));
    }

    @GetMapping("/admin/process-mappings")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    public ApiResponse<PagedResult<IndustryProcessMap>> listProcessMappings(@RequestParam(required = false) String industryCode,
                                                                             @RequestParam(required = false) String processName,
                                                                             @RequestParam(defaultValue = "1") Integer page,
                                                                             @RequestParam(defaultValue = "20") Integer size) {
        return ApiResponse.success(industryService.listProcessMappings(industryCode, processName, page, size));
    }

    @PostMapping("/admin/process-mappings")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    public ApiResponse<IndustryProcessMap> createProcessMapping(@RequestBody @Valid IndustryProcessMappingUpsertRequest request) {
        IndustryProcessMap created = industryService.createProcessMapping(request);
        auditService.log(SecurityUtils.currentUserId(), "INDUSTRY_MAPPING", "CREATE_PROCESS_MAPPING",
            String.valueOf(created.getId()), created.getIndustryCode() + "-" + created.getProcessNamesText());
        return ApiResponse.success(created);
    }

    @PutMapping("/admin/process-mappings/{id}")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    public ApiResponse<IndustryProcessMap> updateProcessMapping(@PathVariable Long id,
                                                                @RequestBody @Valid IndustryProcessMappingUpsertRequest request) {
        IndustryProcessMap updated = industryService.updateProcessMapping(id, request);
        auditService.log(SecurityUtils.currentUserId(), "INDUSTRY_MAPPING", "UPDATE_PROCESS_MAPPING",
            String.valueOf(updated.getId()), updated.getIndustryCode() + "-" + updated.getProcessNamesText());
        return ApiResponse.success(updated);
    }

    @DeleteMapping("/admin/process-mappings/{id}")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    public ApiResponse<String> deleteProcessMapping(@PathVariable Long id) {
        industryService.deleteProcessMapping(id);
        auditService.log(SecurityUtils.currentUserId(), "INDUSTRY_MAPPING", "DELETE_PROCESS_MAPPING", String.valueOf(id), "删除工序映射");
        return ApiResponse.success("删除成功", "OK");
    }

    @GetMapping("/admin/equipment-mappings")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    public ApiResponse<PagedResult<ProcessEquipmentMap>> listEquipmentMappings(@RequestParam(required = false) String processName,
                                                                                @RequestParam(required = false) String equipmentName,
                                                                                @RequestParam(defaultValue = "1") Integer page,
                                                                                @RequestParam(defaultValue = "20") Integer size) {
        return ApiResponse.success(industryService.listEquipmentMappings(processName, equipmentName, page, size));
    }

    @PostMapping("/admin/equipment-mappings")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    public ApiResponse<ProcessEquipmentMap> createEquipmentMapping(@RequestBody @Valid ProcessEquipmentMappingUpsertRequest request) {
        ProcessEquipmentMap created = industryService.createEquipmentMapping(request);
        auditService.log(SecurityUtils.currentUserId(), "INDUSTRY_MAPPING", "CREATE_EQUIPMENT_MAPPING",
            String.valueOf(created.getId()), created.getProcessName() + "-" + created.getEquipmentNamesText());
        return ApiResponse.success(created);
    }

    @PutMapping("/admin/equipment-mappings/{id}")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    public ApiResponse<ProcessEquipmentMap> updateEquipmentMapping(@PathVariable Long id,
                                                                   @RequestBody @Valid ProcessEquipmentMappingUpsertRequest request) {
        ProcessEquipmentMap updated = industryService.updateEquipmentMapping(id, request);
        auditService.log(SecurityUtils.currentUserId(), "INDUSTRY_MAPPING", "UPDATE_EQUIPMENT_MAPPING",
            String.valueOf(updated.getId()), updated.getProcessName() + "-" + updated.getEquipmentNamesText());
        return ApiResponse.success(updated);
    }

    @DeleteMapping("/admin/equipment-mappings/{id}")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    public ApiResponse<String> deleteEquipmentMapping(@PathVariable Long id) {
        industryService.deleteEquipmentMapping(id);
        auditService.log(SecurityUtils.currentUserId(), "INDUSTRY_MAPPING", "DELETE_EQUIPMENT_MAPPING", String.valueOf(id), "删除设备映射");
        return ApiResponse.success("删除成功", "OK");
    }
}
