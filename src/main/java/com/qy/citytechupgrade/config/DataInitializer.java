package com.qy.citytechupgrade.config;

import com.qy.citytechupgrade.common.enums.RoleCode;
import com.qy.citytechupgrade.common.enums.UserStatus;
import com.qy.citytechupgrade.enterprise.EnterpriseProfile;
import com.qy.citytechupgrade.enterprise.EnterpriseProfileRepository;
import com.qy.citytechupgrade.user.*;
import com.qy.citytechupgrade.workflow.WfTemplate;
import com.qy.citytechupgrade.workflow.WfTemplateNode;
import com.qy.citytechupgrade.workflow.WfTemplateNodeRepository;
import com.qy.citytechupgrade.workflow.WfTemplateRepository;
import com.qy.citytechupgrade.workflow.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.init", name = "demo-data-enabled", havingValue = "true", matchIfMissing = true)
public class DataInitializer implements CommandLineRunner {
    private static final String SUPER_ADMIN_USERNAME = "qydevelop";
    private static final String LEGACY_ADMIN_USERNAME = "admin";
    private static final String SUPER_ADMIN_PASSWORD = "qydevelop!@#123";

    private final SysRoleRepository sysRoleRepository;
    private final SysUserRepository sysUserRepository;
    private final SysUserRoleRepository sysUserRoleRepository;
    private final EnterpriseProfileRepository enterpriseProfileRepository;
    private final WfTemplateRepository wfTemplateRepository;
    private final WfTemplateNodeRepository wfTemplateNodeRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        initRoles();
        EnterpriseProfile enterprise = initEnterprise();
        initUsers(enterprise.getId());
        initWorkflowTemplate();
    }

    private void initRoles() {
        for (RoleCode code : RoleCode.values()) {
            if (sysRoleRepository.findByRoleCode(code.name()).isEmpty()) {
                SysRole role = new SysRole();
                role.setRoleCode(code.name());
                role.setRoleName(code.name());
                sysRoleRepository.save(role);
            }
        }
    }

    private EnterpriseProfile initEnterprise() {
        return enterpriseProfileRepository.findByCreditCode("91441900TEST00001X").orElseGet(() -> {
            EnterpriseProfile e = new EnterpriseProfile();
            e.setCreditCode("91441900TEST00001X");
            e.setEnterpriseName("示例技改企业");
            e.setIndustryCode("392");
            e.setIndustryName("通信设备制造");
            e.setAddress("东莞市示例街道88号");
            e.setDataSource("LOCAL");
            return enterpriseProfileRepository.save(e);
        });
    }

    private void initUsers(Long enterpriseId) {
        SysUser admin = ensureSuperAdminUser();
        cleanupLegacyAdminUser(admin.getId());
        SysUser approver = createIfMissing("approver", "审批管理员", null, "Admin@123");
        SysUser enterprise = createIfMissing("enterprise", "企业填报员", enterpriseId, "Admin@123");

        bindRoles(admin.getId(), List.of(RoleCode.SYS_ADMIN.name()));
        bindRoles(approver.getId(), List.of(RoleCode.APPROVER_ADMIN.name()));
        bindRoles(enterprise.getId(), List.of(RoleCode.ENTERPRISE_USER.name()));
    }

    private SysUser ensureSuperAdminUser() {
        SysUser user = sysUserRepository.findByUsername(SUPER_ADMIN_USERNAME).orElse(null);
        if (user == null) {
            user = sysUserRepository.findByUsername(LEGACY_ADMIN_USERNAME).orElseGet(SysUser::new);
            user.setUsername(SUPER_ADMIN_USERNAME);
        }
        user.setDisplayName("系统管理员");
        user.setEnterpriseId(null);
        user.setPasswordHash(passwordEncoder.encode(SUPER_ADMIN_PASSWORD));
        user.setStatus(UserStatus.ACTIVE);
        return sysUserRepository.save(user);
    }

    private void cleanupLegacyAdminUser(Long superAdminUserId) {
        SysUser legacy = sysUserRepository.findByUsername(LEGACY_ADMIN_USERNAME).orElse(null);
        if (legacy == null || Objects.equals(legacy.getId(), superAdminUserId)) {
            return;
        }
        sysUserRoleRepository.deleteByUserId(legacy.getId());
        sysUserRoleRepository.flush();
        sysUserRepository.delete(legacy);
    }

    private SysUser createIfMissing(String username, String displayName, Long enterpriseId, String rawPassword) {
        return sysUserRepository.findByUsername(username).orElseGet(() -> {
            SysUser user = new SysUser();
            user.setUsername(username);
            user.setDisplayName(displayName);
            user.setEnterpriseId(enterpriseId);
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
            user.setStatus(UserStatus.ACTIVE);
            return sysUserRepository.save(user);
        });
    }

    private void bindRoles(Long userId, List<String> roleCodes) {
        List<Long> existingRoleIds = sysUserRoleRepository.findByUserId(userId).stream().map(SysUserRole::getRoleId).toList();
        List<Long> targetRoleIds = sysRoleRepository.findByRoleCodeIn(roleCodes).stream().map(SysRole::getId).toList();
        if (existingRoleIds.containsAll(targetRoleIds) && targetRoleIds.containsAll(existingRoleIds)) {
            return;
        }
        sysUserRoleRepository.deleteByUserId(userId);
        for (Long roleId : targetRoleIds) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            sysUserRoleRepository.save(ur);
        }
    }

    private void initWorkflowTemplate() {
        if (wfTemplateRepository.findFirstByBusinessTypeAndActiveTrueOrderByUpdatedAtDesc(WorkflowService.BIZ_TYPE_SUBMISSION).isPresent()) {
            return;
        }
        WfTemplate template = new WfTemplate();
        template.setBusinessType(WorkflowService.BIZ_TYPE_SUBMISSION);
        template.setTemplateName("默认企业填报审批流");
        template.setActive(true);
        template.setApprovalEnabled(true);
        template = wfTemplateRepository.save(template);

        WfTemplateNode n1 = new WfTemplateNode();
        n1.setTemplateId(template.getId());
        n1.setNodeSeq(1);
        n1.setNodeName("科室审批");
        n1.setRoleCode(RoleCode.APPROVER_ADMIN.name());
        n1.setAllowApprove(true);
        n1.setAllowReject(true);
        n1.setAllowReturn(true);
        wfTemplateNodeRepository.save(n1);

        WfTemplateNode n2 = new WfTemplateNode();
        n2.setTemplateId(template.getId());
        n2.setNodeSeq(2);
        n2.setNodeName("终审");
        n2.setRoleCode(RoleCode.SYS_ADMIN.name());
        n2.setAllowApprove(true);
        n2.setAllowReject(true);
        n2.setAllowReturn(true);
        wfTemplateNodeRepository.save(n2);
    }
}
