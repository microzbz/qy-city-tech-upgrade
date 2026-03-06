package com.qy.citytechupgrade.auth;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class LoginResponse {
    private String accessToken;
    private UserInfo userInfo;
    private List<String> roles;

    @Data
    @Builder
    public static class UserInfo {
        private Long userId;
        private String username;
        private String displayName;
        private Long enterpriseId;
    }
}
