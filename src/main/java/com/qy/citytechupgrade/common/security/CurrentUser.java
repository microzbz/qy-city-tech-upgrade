package com.qy.citytechupgrade.common.security;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Set;

@Data
@AllArgsConstructor
public class CurrentUser {
    private Long userId;
    private String username;
    private String displayName;
    private Long enterpriseId;
    private Set<String> roles;
}
