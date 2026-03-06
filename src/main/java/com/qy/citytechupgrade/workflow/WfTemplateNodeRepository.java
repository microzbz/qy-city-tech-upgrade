package com.qy.citytechupgrade.workflow;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WfTemplateNodeRepository extends JpaRepository<WfTemplateNode, Long> {
    List<WfTemplateNode> findByTemplateIdOrderByNodeSeqAsc(Long templateId);

    void deleteByTemplateId(Long templateId);
}
