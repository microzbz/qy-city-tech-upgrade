package com.qy.citytechupgrade.submission;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class SubmissionSaveRequest {
    private Long submissionId;
    private Integer reportYear;
    private BasicInfo basicInfo;
    private DeviceInfo deviceInfo;
    private DigitalInfo digitalInfo;
    private RdToolInfo rdToolInfo;

    @Data
    public static class BasicInfo {
        private String enterpriseName;
        private String creditCode;
        private String unitNature;
        private String address;
        private LocalDate establishedAt;
        private BigDecimal registerCapital;
        private String legalPerson;
        private String mainProduct;
        private Integer employeeCount;
        private String industryCode;
        private String industryName;
        private BigDecimal annualRevenue2023;
        private BigDecimal annualRevenue2024;
        private BigDecimal annualRevenue2025;
    }

    @Data
    public static class DeviceInfo {
        private List<String> selectedProcesses;
        private List<String> selectedEquipments;
        private List<String> infoDevices;
        private String otherProcess;
        private String otherEquipment;
        private String otherInfoDevice;
    }

    @Data
    public static class DigitalInfo {
        private List<String> digitalSystems;
        private String otherSystem;
    }

    @Data
    public static class RdToolInfo {
        private List<String> rdTools;
        private String otherTool;
    }
}
