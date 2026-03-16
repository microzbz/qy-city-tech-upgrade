package com.qy.citytechupgrade.submission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "submission_device_info")
public class SubmissionDeviceInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "submission_id", nullable = false, unique = true)
    private Long submissionId;

    @Column(name = "selected_processes_json", length = 2000)
    private String selectedProcessesJson;

    @Column(name = "selected_equipments_json", length = 2000)
    private String selectedEquipmentsJson;

    @Column(name = "info_devices_json", length = 2000)
    private String infoDevicesJson;

    @Column(name = "other_process", length = 1000)
    private String otherProcess;

    @Column(name = "other_equipment", length = 1000)
    private String otherEquipment;

    @Column(name = "other_info_device", length = 1000)
    private String otherInfoDevice;

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
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
