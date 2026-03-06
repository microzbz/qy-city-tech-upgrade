package com.qy.citytechupgrade.submission;

import jakarta.persistence.*;
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

    @Lob
    @Column(name = "selected_processes_json")
    private String selectedProcessesJson;

    @Lob
    @Column(name = "selected_equipments_json")
    private String selectedEquipmentsJson;

    @Lob
    @Column(name = "info_devices_json")
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
