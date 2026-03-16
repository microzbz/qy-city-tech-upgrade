package com.qy.citytechupgrade.enterprise;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "survey_enterprise_list")
public class SurveyEnterprise {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "town_park", nullable = false, length = 100)
    private String townPark;

    @Column(name = "enterprise_name", nullable = false, length = 255)
    private String enterpriseName;

    @Column(name = "industry_code", length = 20)
    private String industryCode;

    @Column(name = "enterprise_code_first_digit", length = 1)
    private String enterpriseCodeFirstDigit;

    @Column(name = "enterprise_code_town_digits", length = 2)
    private String enterpriseCodeTownDigits;

    @Column(name = "enterprise_code_industry_digits", length = 2)
    private String enterpriseCodeIndustryDigits;

    @Column(name = "enterprise_code_sequence_digits", length = 3)
    private String enterpriseCodeSequenceDigits;

    @Column(name = "source_row_no", nullable = false)
    private Integer sourceRowNo;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
