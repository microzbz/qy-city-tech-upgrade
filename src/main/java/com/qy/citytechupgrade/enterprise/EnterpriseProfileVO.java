package com.qy.citytechupgrade.enterprise;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class EnterpriseProfileVO {
    private Long id;
    private String creditCode;
    private String enterpriseName;
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
    private String contactName;
    private String contactPhone;
    private String dataSource;

    public static EnterpriseProfileVO from(EnterpriseProfile p) {
        return EnterpriseProfileVO.builder()
            .id(p.getId())
            .creditCode(p.getCreditCode())
            .enterpriseName(p.getEnterpriseName())
            .unitNature(p.getUnitNature())
            .address(p.getAddress())
            .establishedAt(p.getEstablishedAt())
            .registerCapital(p.getRegisterCapital())
            .legalPerson(p.getLegalPerson())
            .mainProduct(p.getMainProduct())
            .employeeCount(p.getEmployeeCount())
            .industryCode(p.getIndustryCode())
            .industryName(p.getIndustryName())
            .annualRevenue2023(p.getAnnualRevenue2023())
            .annualRevenue2024(p.getAnnualRevenue2024())
            .annualRevenue2025(p.getAnnualRevenue2025())
            .contactName(p.getContactName())
            .contactPhone(p.getContactPhone())
            .dataSource(p.getDataSource())
            .build();
    }
}
