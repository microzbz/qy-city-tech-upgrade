package com.qy.citytechupgrade.user;

import lombok.Data;

import java.util.List;

@Data
public class AssignRoleRequest {
    private List<String> roleCodes;
}
