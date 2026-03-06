package com.qy.citytechupgrade.industry;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProcessEquipmentMappingUpsertRequest {
    @NotBlank(message = "主要工序不能为空")
    private String processName;

    @NotBlank(message = "主要设备不能为空")
    private String equipmentNamesText;
}
