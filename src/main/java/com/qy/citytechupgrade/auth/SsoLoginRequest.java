package com.qy.citytechupgrade.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SsoLoginRequest {
    @NotBlank(message = "token不能为空")
    private String token;
}
