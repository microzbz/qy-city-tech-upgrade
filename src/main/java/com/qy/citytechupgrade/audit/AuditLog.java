package com.qy.citytechupgrade.audit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "audit_log")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "module_name", nullable = false, length = 64)
    private String moduleName;

    @Column(name = "action_name", nullable = false, length = 128)
    private String actionName;

    @Column(name = "business_id", length = 128)
    private String businessId;

    @Column(name = "detail_text", length = 2000)
    private String detailText;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
