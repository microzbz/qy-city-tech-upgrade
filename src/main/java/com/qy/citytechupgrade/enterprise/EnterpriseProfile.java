package com.qy.citytechupgrade.enterprise;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "enterprise_profile")
public class EnterpriseProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "credit_code", nullable = false, unique = true, length = 64)
    private String creditCode;

    @Column(name = "enterprise_name", nullable = false, length = 255)
    private String enterpriseName;

    @Column(name = "unit_nature", length = 64)
    private String unitNature;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "established_at")
    private LocalDate establishedAt;

    @Column(name = "register_capital", precision = 18, scale = 2)
    private BigDecimal registerCapital;

    @Column(name = "legal_person", length = 128)
    private String legalPerson;

    @Column(name = "main_product", length = 500)
    private String mainProduct;

    @Column(name = "employee_count")
    private Integer employeeCount;

    @Column(name = "industry_code", length = 10)
    private String industryCode;

    @Column(name = "industry_name", length = 255)
    private String industryName;

    @Column(name = "annual_revenue_2023", precision = 18, scale = 2)
    private BigDecimal annualRevenue2023;

    @Column(name = "annual_revenue_2024", precision = 18, scale = 2)
    private BigDecimal annualRevenue2024;

    @Column(name = "annual_revenue_2025", precision = 18, scale = 2)
    private BigDecimal annualRevenue2025;

    @Column(name = "contact_name", length = 128)
    private String contactName;

    @Column(name = "contact_phone", length = 64)
    private String contactPhone;

    @Column(name = "data_source", nullable = false, length = 32)
    private String dataSource;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (dataSource == null) {
            dataSource = "LOCAL";
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
