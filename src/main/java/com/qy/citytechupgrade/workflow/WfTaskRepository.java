package com.qy.citytechupgrade.workflow;

import com.qy.citytechupgrade.common.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WfTaskRepository extends JpaRepository<WfTask, Long> {
    List<WfTask> findByStatusAndRoleCodeInOrderByCreatedAtDesc(TaskStatus status, List<String> roleCodes);

    List<WfTask> findByStatusOrderByUpdatedAtDesc(TaskStatus status);

    List<WfTask> findByInstanceIdOrderByNodeSeqAsc(Long instanceId);
}
