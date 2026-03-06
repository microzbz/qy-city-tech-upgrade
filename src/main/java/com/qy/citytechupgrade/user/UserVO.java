package com.qy.citytechupgrade.user;

import com.qy.citytechupgrade.common.enums.UserStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class UserVO {
    private Long id;
    private String username;
    private String displayName;
    private UserStatus status;
    private Long enterpriseId;
    private LocalDateTime lastLoginAt;
    private List<String> roleCodes;

    public static UserVO from(SysUser user, List<String> roles) {
        return UserVO.builder()
            .id(user.getId())
            .username(user.getUsername())
            .displayName(user.getDisplayName())
            .status(user.getStatus())
            .enterpriseId(user.getEnterpriseId())
            .lastLoginAt(user.getLastLoginAt())
            .roleCodes(roles)
            .build();
    }
}
