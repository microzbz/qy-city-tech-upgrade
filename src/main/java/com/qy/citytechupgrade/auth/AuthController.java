package com.qy.citytechupgrade.auth;

import com.qy.citytechupgrade.audit.AuditService;
import com.qy.citytechupgrade.common.dto.ApiResponse;
import com.qy.citytechupgrade.common.security.CurrentUser;
import com.qy.citytechupgrade.common.security.SecurityUtils;
import com.qy.citytechupgrade.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    private final AuthService authService;
    private final UserService userService;
    private final AuditService auditService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/sso-login")
    public ApiResponse<LoginResponse> ssoLogin(@RequestBody @Valid SsoLoginRequest request) {
        String code = request.getToken();
        log.info("[单点登录] 收到 /api/auth/sso-login 请求，request={}", request);
        try {
            LoginResponse response = authService.ssoLogin(code);
            ApiResponse<LoginResponse> apiResponse = ApiResponse.success(response);
            log.info("[单点登录] /api/auth/sso-login 调用成功，userId={}，username={}",
                response.getUserInfo() == null ? null : response.getUserInfo().getUserId(),
                response.getUserInfo() == null ? null : response.getUserInfo().getUsername());
            log.info("[单点登录] /api/auth/sso-login 响应={}", apiResponse);
            return apiResponse;
        } catch (Exception e) {
            log.error("[单点登录] /api/auth/sso-login 调用失败，token={}，message={}", code, e.getMessage(), e);
            throw e;
        }
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me() {
        CurrentUser currentUser = SecurityUtils.currentUser();
        Map<String, Object> data = new HashMap<>();
        data.put("userInfo", authService.me(currentUser));
        data.put("roles", userService.getRoleCodesByUserId(currentUser.getUserId()));
        data.put("menus", userService.getMenusByRoles(userService.getRoleCodesByUserId(currentUser.getUserId())));
        return ApiResponse.success(data);
    }

    @PostMapping("/logout")
    public ApiResponse<String> logout() {
        auditService.log(SecurityUtils.currentUserId(), "AUTH", "LOGOUT", null, "退出登录");
        return ApiResponse.success("退出成功", "OK");
    }
}
