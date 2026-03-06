package com.qy.citytechupgrade.workflow;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "wf_template_node")
public class WfTemplateNode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "node_seq", nullable = false)
    private Integer nodeSeq;

    @Column(name = "node_name", nullable = false, length = 128)
    private String nodeName;

    @Column(name = "role_code", nullable = false, length = 64)
    private String roleCode;

    @Column(name = "allow_approve", nullable = false)
    private Boolean allowApprove;

    @Column(name = "allow_reject", nullable = false)
    private Boolean allowReject;

    @Column(name = "allow_return", nullable = false)
    private Boolean allowReturn;

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
        if (allowApprove == null) {
            allowApprove = true;
        }
        if (allowReject == null) {
            allowReject = true;
        }
        if (allowReturn == null) {
            allowReturn = true;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
