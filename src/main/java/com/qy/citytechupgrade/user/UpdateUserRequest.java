package com.qy.citytechupgrade.user;

import com.qy.citytechupgrade.common.enums.UserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateUserRequest {
    @NotBlank(message = "姓名不能为空")
    private String displayName;

    private String password;

    @NotNull(message = "状态不能为空")
    private UserStatus status;

    private Long enterpriseId;
}
