package com.qy.citytechupgrade.common.security;

import com.qy.citytechupgrade.common.exception.BizException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {
    private SecurityUtils() {
    }

    public static CurrentUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CurrentUser principal)) {
            throw new BizException("未登录或登录状态失效");
        }
        return principal;
    }

    public static Long currentUserId() {
        return currentUser().getUserId();
    }
}
