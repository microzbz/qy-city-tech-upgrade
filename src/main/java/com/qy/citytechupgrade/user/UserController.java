package com.qy.citytechupgrade.user;

import com.qy.citytechupgrade.audit.AuditService;
import com.qy.citytechupgrade.common.dto.ApiResponse;
import com.qy.citytechupgrade.common.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final AuditService auditService;

    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('SYS_ADMIN','APPROVER_ADMIN')")
    public ApiResponse<List<UserVO>> listUsers() {
        return ApiResponse.success(userService.listUsers());
    }

    @PostMapping("/users")
    @PreAuthorize("hasAnyRole('SYS_ADMIN','APPROVER_ADMIN')")
    public ApiResponse<UserVO> createUser(@RequestBody @Valid CreateUserRequest request) {
        UserVO created = userService.createUser(request);
        auditService.log(SecurityUtils.currentUserId(), "USER", "CREATE_USER",
            String.valueOf(created.getId()), created.getUsername() + "/" + created.getDisplayName());
        return ApiResponse.success(created);
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasAnyRole('SYS_ADMIN','APPROVER_ADMIN')")
    public ApiResponse<UserVO> updateUser(@PathVariable Long id, @RequestBody @Valid UpdateUserRequest request) {
        UserVO updated = userService.updateUser(id, request);
        auditService.log(SecurityUtils.currentUserId(), "USER", "UPDATE_USER",
            String.valueOf(updated.getId()), updated.getUsername() + "/" + updated.getDisplayName());
        return ApiResponse.success(updated);
    }

    @PutMapping("/users/{id}/roles")
    @PreAuthorize("hasAnyRole('SYS_ADMIN','APPROVER_ADMIN')")
    public ApiResponse<String> assignRoles(@PathVariable Long id, @RequestBody AssignRoleRequest request) {
        userService.assignRoles(id, request.getRoleCodes());
        auditService.log(SecurityUtils.currentUserId(), "USER", "ASSIGN_ROLE", String.valueOf(id),
            "角色=" + (request.getRoleCodes() == null ? "[]" : request.getRoleCodes()));
        return ApiResponse.success("角色更新成功", "OK");
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAnyRole('SYS_ADMIN','APPROVER_ADMIN')")
    public ApiResponse<List<SysRole>> listRoles() {
        return ApiResponse.success(userService.listRoles());
    }
}
