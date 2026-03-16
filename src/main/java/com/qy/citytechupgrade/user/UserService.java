package com.qy.citytechupgrade.user;

import com.qy.citytechupgrade.common.dto.PagedResult;
import com.qy.citytechupgrade.common.enums.RoleCode;
import com.qy.citytechupgrade.common.enums.UserStatus;
import com.qy.citytechupgrade.common.exception.BizException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {
    private static final String HIDDEN_ROLE_CODE = RoleCode.SYS_ADMIN.name();

    private final SysUserRepository sysUserRepository;
    private final SysRoleRepository sysRoleRepository;
    private final SysUserRoleRepository sysUserRoleRepository;
    private final PasswordEncoder passwordEncoder;

    public Optional<SysUser> findByUsername(String username) {
        return sysUserRepository.findByUsername(username);
    }

    public SysUser save(SysUser user) {
        return sysUserRepository.save(user);
    }

    public List<String> getRoleCodesByUserId(Long userId) {
        List<Long> roleIds = sysUserRoleRepository.findByUserId(userId).stream().map(SysUserRole::getRoleId).toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return sysRoleRepository.findAllById(roleIds).stream().map(SysRole::getRoleCode).toList();
    }

    public List<SysRole> listRoles() {
        return sysRoleRepository.findAll().stream()
            .filter(role -> !HIDDEN_ROLE_CODE.equals(role.getRoleCode()))
            .toList();
    }

    public List<SysUser> listUsersByRoleCode(String roleCode) {
        SysRole role = sysRoleRepository.findByRoleCode(roleCode).orElse(null);
        if (role == null) {
            return List.of();
        }
        List<Long> userIds = sysUserRoleRepository.findByRoleId(role.getId()).stream().map(SysUserRole::getUserId).toList();
        if (userIds.isEmpty()) {
            return List.of();
        }
        return sysUserRepository.findAllById(userIds);
    }

    public List<Map<String, Object>> getMenusByRoles(List<String> roles) {
        Set<String> set = new HashSet<>(roles);
        List<Map<String, Object>> menus = new ArrayList<>();

        if (set.contains("ENTERPRISE_USER")) {
            menus.add(menu("submission", "企业填报", "/enterprise/submission"));
            menus.add(menu("my-submissions", "我的填报", "/enterprise/my-submissions"));
            menus.add(menu("notices", "我的消息", "/common/notices"));
        }
        if (set.contains("APPROVER_ADMIN")) {
            menus.add(menu("approval-todo", "待审批", "/approvals/todo"));
            menus.add(menu("approval-done", "已审批", "/approvals/done"));
            menus.add(menu("users", "用户管理", "/admin/users"));
            menus.add(menu("audit", "审计日志", "/admin/audit-logs"));
            menus.add(menu("notices", "我的消息", "/common/notices"));
        }
        if (set.contains("SYS_ADMIN")) {
            menus.add(menu("users", "用户管理", "/admin/users"));
            menus.add(menu("industry-mappings", "行业映射", "/admin/industry-mappings"));
            menus.add(menu("survey-enterprises", "调研企业库", "/admin/survey-enterprises"));
            menus.add(menu("submission-options", "填报选项", "/admin/submission-options"));
            menus.add(menu("workflow", "流程模板", "/admin/workflow"));
            menus.add(menu("audit", "审计日志", "/admin/audit-logs"));
            menus.add(menu("notices", "我的消息", "/common/notices"));
        }

        return menus;
    }

    private Map<String, Object> menu(String code, String name, String path) {
        Map<String, Object> menu = new HashMap<>();
        menu.put("code", code);
        menu.put("name", name);
        menu.put("path", path);
        return menu;
    }

    public PagedResult<UserVO> listUsers(Integer page, Integer size) {
        List<SysRole> roles = sysRoleRepository.findAll();
        Map<Long, String> roleMap = roles.stream().collect(Collectors.toMap(SysRole::getId, SysRole::getRoleCode));

        List<UserVO> users = sysUserRepository.findAll().stream()
            .map(u -> {
                List<String> roleCodes = sysUserRoleRepository.findByUserId(u.getId()).stream()
                    .map(SysUserRole::getRoleId)
                    .map(roleMap::get)
                    .filter(Objects::nonNull)
                    .toList();
                return UserVO.from(u, roleCodes);
            })
            .filter(vo -> vo.getRoleCodes() == null || vo.getRoleCodes().stream().noneMatch(HIDDEN_ROLE_CODE::equals))
            .toList();
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        int fromIndex = Math.min((safePage - 1) * safeSize, users.size());
        int toIndex = Math.min(fromIndex + safeSize, users.size());
        return PagedResult.of(users.subList(fromIndex, toIndex), users.size(), safePage, safeSize);
    }

    @Transactional
    public UserVO createUser(CreateUserRequest request) {
        if (sysUserRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new BizException("用户名已存在");
        }

        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setDisplayName(request.getDisplayName());
        user.setEnterpriseId(request.getEnterpriseId());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setStatus(UserStatus.ACTIVE);
        sysUserRepository.save(user);

        assignRoles(user.getId(), request.getRoleCodes());
        return UserVO.from(user, getRoleCodesByUserId(user.getId()));
    }

    @Transactional
    public UserVO updateUser(Long userId, UpdateUserRequest request) {
        SysUser user = sysUserRepository.findById(userId).orElseThrow(() -> new BizException("用户不存在"));
        user.setDisplayName(request.getDisplayName());
        user.setEnterpriseId(request.getEnterpriseId());
        user.setStatus(request.getStatus());
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }
        sysUserRepository.save(user);
        return UserVO.from(user, getRoleCodesByUserId(user.getId()));
    }

    @Transactional
    public void assignRoles(Long userId, List<String> roleCodes) {
        SysUser user = sysUserRepository.findById(userId).orElseThrow(() -> new BizException("用户不存在"));
        List<String> normalizedRoleCodes = roleCodes == null ? List.of() : roleCodes.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .distinct()
            .toList();
        if (normalizedRoleCodes.stream().anyMatch(HIDDEN_ROLE_CODE::equals)) {
            throw new BizException("不允许在用户管理中分配SYS_ADMIN角色");
        }
        List<SysRole> roles = normalizedRoleCodes.isEmpty() ? List.of() : sysRoleRepository.findByRoleCodeIn(normalizedRoleCodes);
        if (normalizedRoleCodes.size() != roles.size()) {
            throw new BizException("存在无效角色编码");
        }

        sysUserRoleRepository.deleteByUserId(user.getId());
        sysUserRoleRepository.flush();
        for (SysRole role : roles) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(user.getId());
            ur.setRoleId(role.getId());
            sysUserRoleRepository.save(ur);
        }
    }

    @Transactional
    public void ensureRole(Long userId, String roleCode) {
        SysUser user = sysUserRepository.findById(userId).orElseThrow(() -> new BizException("用户不存在"));
        SysRole role = sysRoleRepository.findByRoleCode(roleCode).orElseThrow(() -> new BizException("角色不存在: " + roleCode));
        boolean exists = sysUserRoleRepository.findByUserId(user.getId()).stream()
            .anyMatch(ur -> Objects.equals(ur.getRoleId(), role.getId()));
        if (exists) {
            return;
        }
        SysUserRole ur = new SysUserRole();
        ur.setUserId(user.getId());
        ur.setRoleId(role.getId());
        sysUserRoleRepository.save(ur);
    }
}
